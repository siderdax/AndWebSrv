#####################################################################
# the build script for NDK for droidipcam project
#

LOCAL_PATH:= $(call my-dir)

###########################################################
#   uPnP port mapping 
#
include $(CLEAR_VARS)
LOCAL_MODULE := libnatpmp
LOCAL_CFLAGS := -O2 -Wall -DANDROID -DLINUX -I./libnatpmp/  

#including source files
include $(LOCAL_PATH)/libnatpmp_build.mk

LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)   
