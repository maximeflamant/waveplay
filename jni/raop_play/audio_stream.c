/*****************************************************************************
 * audio_stream.c: audio file stream
 *
 * Copyright (C) 2005 Shiro Ninomiya <shiron@snino.com>
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
#include <asm/types.h>
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <samplerate.h>
#define AUDIO_STREAM_C
#include "audio_stream.h"
#include "pcm_stream.h"
#include "aexcl_lib.h"


static data_type_t get_data_type(char *fname);

auds_t *auds_open(char *fname, data_type_t adt)
{
	auds_t *auds=malloc(sizeof(auds_t));
	if(!auds) return NULL;
	memset(auds, 0, sizeof(auds_t));
	int rval=-1;
	int err;

	auds->channels=2; //default is stereo
	if(adt==AUD_TYPE_NONE)
		auds->data_type=get_data_type(fname);
	else
		auds->data_type=adt;
	switch(auds->data_type){
	
	case AUD_TYPE_PCM:
		rval=pcm_open(auds, fname);
		break;
	case AUD_TYPE_NONE:
		ERRMSG("unknown audio data type\n");
		break;
	}
	if(rval) goto erexit;
	return auds;
 erexit:
	ERRMSG("errror: %s\n",__func__);
	auds_close(auds);
	return NULL;
}

int auds_close(auds_t *auds)
{
	if(auds->stream){
		switch(auds->data_type){

		case AUD_TYPE_PCM:
			pcm_close(auds);
			break;
		case AUD_TYPE_NONE:
			ERRMSG("### shouldn't come here\n");
			break;
		}
	}

	free(auds);
	return 0;
}

int auds_get_top_sample(auds_t *auds, __u8 **data, int *size)
{
		ERRMSG("%s:### shouldn't come here\n",__func__);
		return -1;
}

int auds_get_next_sample(auds_t *auds, __u8 **data, int *size)
{
	int rval;
	auds_t *lauds=auds;
	if(auds->auds) lauds=auds->auds;
	switch(lauds->data_type){
	case AUD_TYPE_PCM:
		rval=pcm_get_next_sample(lauds, data, size);
		break;
	case AUD_TYPE_NONE:
		ERRMSG("%s:### shouldn't come here\n",__func__);
		return -1;
	}
	return rval;
}

/* return -- 1: the next sample exists, 0: it doesn't */
int auds_poll_next_sample(auds_t *auds)
{
	switch(auds->data_type){
	case AUD_TYPE_PCM:
		return pcm_poll_next_sample(auds);
	default:
		break;
	}
	return 1;
}

int auds_sigchld(auds_t *auds, siginfo_t *siginfo)
{
	if(!auds) return 0;
	if(auds->auds && auds->auds->sigchld_cb){
		auds->auds->sigchld_cb(auds->auds->stream, siginfo);
		return 0;
	}
	if(auds->sigchld_cb){
		auds->sigchld_cb(auds->stream, siginfo);
		return 0;
	}
	return 0;
}


int auds_write_pcm(auds_t *auds, __u8 *buffer, __u8 **data, int *size,
		   int bsize, data_source_t *ds)
{
	__u8 one[4];
	int count=0;
	int bpos=0;
	__u8 *bp=buffer;
	int i,nodata=0;
	__s16 *resamp=NULL, *pr=NULL;
	int channels=2;
	if(auds) channels=auds->channels;

	bits_write(&bp,1,3,&bpos); // channel=1, stereo
	bits_write(&bp,0,4,&bpos); // unknown
	bits_write(&bp,0,8,&bpos); // unknown
	bits_write(&bp,0,4,&bpos); // unknown
	if(bsize!=4096)
		bits_write(&bp,1,1,&bpos); // hassize
	else
		bits_write(&bp,0,1,&bpos); // hassize
	bits_write(&bp,0,2,&bpos); // unused
	bits_write(&bp,1,1,&bpos); // is-not-compressed
	if(bsize!=4096){
		bits_write(&bp,(bsize>>24)&0xff,8,&bpos); // size of data, integer, big endian
		bits_write(&bp,(bsize>>16)&0xff,8,&bpos);
		bits_write(&bp,(bsize>>8)&0xff,8,&bpos);
		bits_write(&bp,bsize&0xff,8,&bpos);
	}
	while(1){
		if(pr){
			if(channels==1)
				*((__s16*)one)=*pr;
			else
				*((__s16*)one)=*pr++;
			*((__s16*)one+1)=*pr++;
		}else {
			switch(ds->type){
			case DESCRIPTOR:
				if(channels==1){
					if(read(ds->u.fd, one, 2)!=2) nodata=1;
					*((__s16*)one+1)=*((__s16*)one);
				}else{
					if(read(ds->u.fd, one, 4)!=4) nodata=1;
				}
				break;
			case STREAM:
				if(channels==1){
					if(fread(one,1,2,ds->u.inf)!=2) nodata=1;
					*((__s16*)one+1)=*((__s16*)one);
				}else{
					if(fread(one,1,4,ds->u.inf)!=4) nodata=1;
				}
				break;
			case MEMORY:
				if(channels==1){
					if(ds->u.mem.size<=count*2) nodata=1;
					*((__s16*)one)=ds->u.mem.data[count];
					*((__s16*)one+1)=*((__s16*)one);
				}else{
					if(ds->u.mem.size<=count*4) nodata=1;
					*((__s16*)one)=ds->u.mem.data[count*2];
					*((__s16*)one+1)=ds->u.mem.data[count*2+1];
				}
				break;
			}
		}
		if(nodata) break;

		bits_write(&bp,one[1],8,&bpos);
		bits_write(&bp,one[0],8,&bpos);
		bits_write(&bp,one[3],8,&bpos);
		bits_write(&bp,one[2],8,&bpos);
		if(++count==bsize) break;
	}
	if(!count) return -1; // when no data at all, it should stop playing
	/* when readable size is less than bsize, fill 0 at the bottom */
	for(i=0;i<(bsize-count)*4;i++){
		bits_write(&bp,0,8,&bpos);
	}
	*size=bp-buffer;
	if(bpos) *size+=1;
	*data=buffer;
	return 0;
}

int aud_clac_chunk_size(int sample_rate)
{
	int bsize=MAX_SAMPLES_IN_CHUNK;
	int ratio=DEFAULT_SAMPLE_RATE*100/sample_rate;
	// to make suer the resampled size is <= 4096
	if(ratio>100) bsize=bsize*100/ratio-1;
	//return bsize;
	return bsize;

}


static data_type_t get_data_type(char *fname)
{
	return AUD_TYPE_PCM;
}

