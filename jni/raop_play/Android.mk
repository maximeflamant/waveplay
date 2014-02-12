# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH = $(ROOT_PATH)/raop_play

include $(CLEAR_VARS)


LOCAL_MODULE    := raop-play
LOCAL_SRC_FILES := aexcl_lib.c base64.c pcm_stream.c raop_client.c rtsp_client.c aes.c audio_stream.c raop_play.c
LOCAL_C_INCLUDES += $(ROOT_PATH)/openssl/include \
			$(ROOT_PATH)/libsamplerate

LOCAL_LDLIBS := -L$(ROOT_PATH)/../obj/local/armeabi -lssl -lcrypto -L$(ROOT_PATH)/../libs/armeabi -lsamplerate -llog

LOCAL_SHARED_LIBRARIES := libssl libcrypto
LOCAL_STATIC_LIBRARIES :=  libsamplerate

include $(BUILD_SHARED_LIBRARY)