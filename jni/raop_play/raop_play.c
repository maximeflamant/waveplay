/*****************************************************************************
 * rtsp_play.c: RAOP Client player
 *
 * Copyright (C) 2004 Shiro Ninomiya <shiron@snino.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111, USA.
 *****************************************************************************/

#include <unistd.h>
#include <stdio.h>
#include <signal.h>
#include <jni.h>
#include "aexcl_lib.h"
#include "raop_client.h"
#include "audio_stream.h"
#include "raop_play.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <android/log.h>

#include<pthread.h>
#include <arpa/inet.h>
#include <sys/socket.h>



#define SERVER_PORT 5000

#define MAX_NUM_OF_FDS 4
typedef struct fdev_t{
	int fd;
	void *dp;
	fd_callback_t cbf;
	int flags;
}dfev_t;

typedef struct raopld_t{
	raopcl_t *raopcl;
	auds_t *auds;
	dfev_t fds[MAX_NUM_OF_FDS];
}raopld_t;
static raopld_t *raopld;

typedef struct JniMethodInfo_
{
	JNIEnv *    env;
	jclass      classID;
	jmethodID   methodID;
} JniMethodInfo;

JNIEnv* jniEnv = NULL;
jobject jniThiz = NULL;
jclass clazz = NULL;
jmethodID notifyMtd = NULL;
jmethodID isActivatedMtd = NULL;


struct arg_struct {
	int argc;
	char **argv;
};

static int print_usage(char *argv[])
{
	printf("%s [--port port_number] [--vol volume(0-100)] "
			"[-i interactive mode] [-e no-encrypt-mode] [--proto [udp|tcp]]  server_ip audio_filename\n",argv[0]);
	return -1;
}

#define MAIN_EVENT_TIMEOUT 3 // sec unit
static int main_event_handler()
{
	fd_set rdfds,wrfds;
	int fdmax=0;
	int i;
	struct timeval tout={.tv_sec=MAIN_EVENT_TIMEOUT, .tv_usec=0};

	FD_ZERO(&rdfds);
	FD_ZERO(&wrfds);
	for(i=0;i<MAX_NUM_OF_FDS;i++){
		if(raopld->fds[i].fd<0) continue;
		if(raopld->fds[i].flags&RAOP_FD_READ)
			FD_SET(raopld->fds[i].fd, &rdfds);
		if(raopld->fds[i].flags&RAOP_FD_WRITE)
			FD_SET(raopld->fds[i].fd, &wrfds);
		fdmax=(fdmax<raopld->fds[i].fd)?raopld->fds[i].fd:fdmax;
	}
	if(raopcl_wait_songdone(raopld->raopcl,0))
		raopcl_aexbuf_time(raopld->raopcl, &tout);

	select(fdmax+1,&rdfds,&wrfds,NULL,&tout);

	for(i=0;i<MAX_NUM_OF_FDS;i++){
		if(raopld->fds[i].fd<0) continue;
		if((raopld->fds[i].flags&RAOP_FD_READ) &&
				FD_ISSET(raopld->fds[i].fd,&rdfds)){
			if(raopld->fds[i].cbf &&
					raopld->fds[i].cbf(raopld->fds[i].dp, RAOP_FD_READ)) return -1;
		}
		if((raopld->fds[i].flags&RAOP_FD_WRITE) &&
				FD_ISSET(raopld->fds[i].fd,&wrfds)){
			if(raopld->fds[i].cbf &&
					raopld->fds[i].cbf(raopld->fds[i].dp, RAOP_FD_WRITE)) return -1;
		}
	}

	if(raopcl_wait_songdone(raopld->raopcl,0)){
		raopcl_aexbuf_time(raopld->raopcl, &tout);
		if(!tout.tv_sec && !tout.tv_usec){
			// AEX data buffer becomes empty, it means end of playing a song.
			printf("%s\n",RAOP_SONGDONE);
			fflush(stdout);
			raopcl_wait_songdone(raopld->raopcl,-1); // clear wait_songdone
		}
	}

	raopcl_pause_check(raopld->raopcl);

	return 0;
}

static int console_command(char *cstr)
{
	int i;
	char *ps=NULL;

	if(strstr(cstr,"play")==cstr){
		if(raopcl_get_pause(raopld->raopcl)) {
			raopcl_set_pause(raopld->raopcl,NO_PAUSE);
			return 0;
		}
		for(i=0;i<strlen(cstr);i++) {
			if(cstr[i]==' ') {
				ps=cstr+i+1;
				break;
			}
		}
		if(!ps) return 0;
		// if there is a new song name, open the song
		if(!(raopld->auds=auds_open(ps, 0))){
			printf("%s\n",RAOP_ERROR);
			fflush(stdout);
			return -1;
		}
		raopcl_flush_stream(raopld->raopcl);
		return 0;
	}else if(!strcmp(cstr,"pause")){
		if(raopcl_wait_songdone(raopld->raopcl,0)){
			INFMSG("in this period, pause can't work\n");
			return -2;
		}
		if(raopld->auds) {
			raopcl_set_pause(raopld->raopcl,OP_PAUSE);
		}
	}else if(!strcmp(cstr,"stop")){
		if(raopcl_get_pause(raopld->raopcl)) raopcl_set_pause(raopld->raopcl,NO_PAUSE);
		if(raopld->auds) auds_close(raopld->auds);
		raopld->auds=NULL;
	}else if(!strcmp(cstr,"quit")){
		return -2;
	}else if((ps=strstr(cstr,"volume"))){
		i=atoi(ps+7);
		return raopcl_update_volume(raopld->raopcl,i);
	}
	return -1;
}

static int console_read(void *p, int flags)
{
	char line[256];
	if(read_line(0,line,sizeof(line),100,0)==-1){
		DBGMSG("stop reading from console\n");
		clear_fd_event(0);		
	}else{
		DBGMSG("%s:%s\n",__func__,line);
	}
	if(console_command(line)==-2) return -1;
	// ignore console command errors, and return 0
	return 0;	
}

static int terminate_cbf(void *p, int flags){
	return -1;
}

static void sig_action(int signo, siginfo_t *siginfo, void *extra)
{
	// SIGCHLD, a child process is terminated
	if(signo==SIGCHLD){
		auds_sigchld(raopld->auds, siginfo);
		return;
	}
	//SIGINT,SIGTERM
	DBGMSG("SIGINT or SIGTERM\n");
	set_fd_event(1,RAOP_FD_WRITE,terminate_cbf,NULL);
	return;
}

int main(int argc, char *argv[])
{
	char *host=NULL;
	char *fname=NULL;
	int port=SERVER_PORT;
	int rval=-1,i;
	int size;
	int volume=100;
	int encrypt=1;
	int udp=0;
	__u8 *buf;
	int iact=0;
	struct sigaction act;

	/* Assign sig_term as our SIGTERM handler */
	act.sa_sigaction = sig_action;
	sigemptyset(&act.sa_mask); // no other signals are blocked
	act.sa_flags = SA_SIGINFO; // use sa_sigaction instead of sa_handler
	sigaction(SIGTERM, &act, NULL);
	sigaction(SIGINT, &act, NULL);
	sigaction(SIGCHLD, &act, NULL);

	for(i=1;i<argc;i++){
		if(!strcmp(argv[i],"-i")){
			iact=1;
			continue;
		}
		if(!strcmp(argv[i],"--port")){
			port=atoi(argv[++i]);
			continue;
		}
		if(!strcmp(argv[i],"--vol")){
			volume=atoi(argv[++i]);
			continue;
		}
		if(!strcmp(argv[i],"-e")){
			encrypt=0;
			continue;
		}
		if(!strcmp(argv[i],"--proto")){
			udp=!strcmp(argv[++i],"udp");
			continue;
		}
		if(!strcmp(argv[i],"--help") || !strcmp(argv[i],"-h"))
			return print_usage(argv);
		if(!host) {host=argv[i]; continue;}
		if(!fname) {fname=argv[i]; continue;}
		INFMSG("raop device %s\n",host);
		INFMSG("raop stream %s\n",fname);



	}
	if(!host) return print_usage(argv);
	if(!iact && !fname) return print_usage(argv);

	raopld=(raopld_t*)malloc(sizeof(raopld_t));
	if(!raopld) goto erexit;
	memset(raopld,0,sizeof(raopld_t));
	for(i=0;i<MAX_NUM_OF_FDS;i++) raopld->fds[i].fd=-1;

	raopld->raopcl=raopcl_open();
	if(!raopld->raopcl) goto erexit;
	if(raopcl_connect(raopld->raopcl,host,port,encrypt,udp)) goto erexit;
	if(raopcl_update_volume(raopld->raopcl,volume)) goto erexit;
	if (udp)
		if(raopcl_set_content(raopld->raopcl, "ROAP PLAY", "Via Waveplay", "")) goto erexit;
	DBGMSG("Connected to Airplay device\n");
	if(fname && !(raopld->auds=auds_open(fname,0))) goto erexit;

	set_fd_event(0,RAOP_FD_READ,console_read,NULL);
	rval=0;

	while(!rval && isActivated(host)==1){
		if(!raopld->auds){
			// if audio data is not opened, just check events
			rval=main_event_handler(raopld);
			continue;
		}
		switch(raopcl_get_pause(raopld->raopcl)){
		case OP_PAUSE:
			rval=main_event_handler();
			continue;
		case NODATA_PAUSE:
			if(auds_poll_next_sample(raopld->auds)){
				raopcl_set_pause(raopld->raopcl,NO_PAUSE);
			}else{
				rval=main_event_handler();
				continue;
			}
		case NO_PAUSE:
			if(!auds_poll_next_sample(raopld->auds)){
				// no next data, turn into pause status
				raopcl_set_pause(raopld->raopcl,NODATA_PAUSE);
				continue;
			}

			if(auds_get_next_sample(raopld->auds, &buf, &size)){
				auds_close(raopld->auds);
				raopld->auds=NULL;
				raopcl_wait_songdone(raopld->raopcl,1);
			}

			if(raopcl_send_sample(raopld->raopcl,buf,size)) break;
			do{
				if((rval=main_event_handler())) break;
			}while(raopld->auds && raopcl_sample_remsize(raopld->raopcl));

			break;
		default:
			rval=-1;
			break;
		}
	}
	rval=raopcl_close(raopld->raopcl);
	erexit:
	if(raopld->auds) auds_close(raopld->auds);
	if(raopld) free(raopld);

	DBGMSG("Stop streaming. %s Active:%i",host,isActivated(host));

	return rval;
}


int set_fd_event(int fd, int flags, fd_callback_t cbf, void *p)
{
	int i;
	// check the same fd first. if it exists, update it
	for(i=0;i<MAX_NUM_OF_FDS;i++){
		if(raopld->fds[i].fd==fd){
			raopld->fds[i].dp=p;
			raopld->fds[i].cbf=cbf;
			raopld->fds[i].flags=flags;
			return 0;
		}
	}
	// then create a new one
	for(i=0;i<MAX_NUM_OF_FDS;i++){
		if(raopld->fds[i].fd<0){
			raopld->fds[i].fd=fd;
			raopld->fds[i].dp=p;
			raopld->fds[i].cbf=cbf;
			raopld->fds[i].flags=flags;
			return 0;
		}
	}
	return -1;
}

int clear_fd_event(int fd)
{
	int i;
	for(i=0;i<MAX_NUM_OF_FDS;i++){
		if(raopld->fds[i].fd==fd){
			raopld->fds[i].fd=-1;
			raopld->fds[i].dp=NULL;
			raopld->fds[i].cbf=NULL;
			raopld->fds[i].flags=0;
			return 0;
		}
	}
	return -1;
}
int
Java_com_nixus_raop_RaopHelper_raopPlay( JNIEnv* env,
		jobject thiz,jobjectArray elements )
{

	char *message[7];
	char out[128];

	jniEnv = env;
	jniThiz = thiz;

    clazz = (*jniEnv)->FindClass(jniEnv, "com/nixus/raop/RaopHelper");
	notifyMtd = (*jniEnv)->GetMethodID(jniEnv, clazz, "notifyReady", "(Z)Z");
	isActivatedMtd = (*jniEnv)->GetMethodID(jniEnv, clazz, "isActivated", "(Ljava/lang/String;)Z");

	int i=0;
	for (i=0; i<7; i++) {
		jstring string =  (* env)->GetObjectArrayElement(env,elements, i);
		const char *rawString = (* env)->GetStringUTFChars(env, string, 0);
		message[i] = rawString;
	}

	main(8,message);

	return -1;


}
// Notify being ready for streaming

void notifyReady(int ready){

	jboolean jbool = ready==0?JNI_FALSE:JNI_TRUE;
	jboolean result = (*jniEnv)->CallBooleanMethod(jniEnv, jniThiz, notifyMtd, jbool);
}

// Check is streaming is still activated on given address
int isActivated(const char * address){


	jstring jstr = (*jniEnv)->NewStringUTF(jniEnv, address);
	jboolean jresult = (*jniEnv)->CallBooleanMethod(jniEnv, jniThiz, isActivatedMtd, jstr);

	(*jniEnv)->DeleteLocalRef(jniEnv,jstr);

	return jresult==JNI_TRUE?1:0;

}
