AM_CPPFLAGS = -I../../.. -I$(srcdir)/../../.. $(JNI_CPPFLAGS) 
AM_CXXFLAGS += -Wno-unused-command-line-argument -Wno-overloaded-virtual
EXTRA_DIST = .indent.pro 

lib_LTLIBRARIES = libtsk_jni.la
libtsk_jni_la_SOURCES = dataModel_SleuthkitJNI.cpp dataModel_SleuthkitJNI.h  auto_db_java.h auto_db_java.cpp
libtsk_jni_la_LIBADD = ../../../tsk/libtsk.la

indent:
	indent *.cpp *.h

clean-local:
	-rm -f *.c~ *.h~
