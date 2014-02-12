#ifndef __RAOP_PLAY_H_
#define __RAOP_PLAY_H


#define	RAOP_FD_READ (1<<0)
#define RAOP_FD_WRITE (1<<1)

#define RAOP_CONNECTED "connected"
#define RAOP_SONGDONE "done"
#define RAOP_ERROR "error"

typedef int (*fd_callback_t)(void *, int);
int set_fd_event(int fd, int flags, fd_callback_t cbf, void *p);
int clear_fd_event(int fd);

#endif
