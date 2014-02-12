# Makefile
#	Copyright (C) Max Kastanas 2010
#	Copyright (C) Naval Saini 2010

# * This program is free software; you can redistribute it and/or modify
# * it under the terms of the GNU General Public License as published by
# * the Free Software Foundation; either version 2 of the License, or
# * (at your option) any later version.
# *
# * This program is distributed in the hope that it will be useful,
# * but WITHOUT ANY WARRANTY; without even the implied warranty of
# * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# * GNU General Public License for more details.
# *
# * You should have received a copy of the GNU General Public License
# * along with this program; if not, write to the Free Software
# * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

LOCAL_PATH := $(ROOT_PATH)/libsamplerate
#$

## libsamplerate module
include $(CLEAR_VARS)

LOCAL_MODULE    := libsamplerate
LOCAL_CFLAGS    := -Werror -g

LOCAL_C_INCLUDES := $(ROOT_PATH)/libsamplerate

LOCAL_SRC_FILES := samplerate.c src_linear.c src_sinc.c src_zoh.c
LOCAL_LDLIBS    := -llog -lm

include $(BUILD_SHARED_LIBRARY)

