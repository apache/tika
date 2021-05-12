#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

if [[ $EUID -ne 0 ]]; then
  echo -e "\nERROR: This script must be run as root\n" 1>&2
  exit 1
fi

print_usage() {
  ERROR_MSG="$1"

  if [ "$ERROR_MSG" != "" ]; then
    echo -e "\nERROR: $ERROR_MSG\n" 1>&2
  fi

  echo ""
  echo "Usage: install_tika_service.sh <path_to_tika_distribution_archive> [OPTIONS]"
  echo ""
  echo "  The first argument to the script must be a path to a Tika distribution archive, such as tika-server-2.0.0-SNAPSHOT.bin.tgz"
  echo "    (only .tgz or .zip are supported formats for the archive)"
  echo ""
  echo "  Supported OPTIONS include:"
  echo ""
  echo "    -d     Directory for live / writable Tika files, such as logs, pid files; defaults to /var/tika"
  echo ""
  echo "    -i     Directory to extract the Tika installation archive; defaults to /opt/"
  echo "             The specified path must exist prior to using this script."
  echo ""
  echo "    -p     Port Tika should bind to; default is 9998"
  echo ""
  echo "    -s     Service name; defaults to tika"
  echo ""
  echo "    -u     User to own the Tika files and run the Tika process as; defaults to tika"
  echo "             This script will create the specified user account if it does not exist."
  echo ""
  echo "    -f     Upgrade Tika. Overwrite symlink and init script of previous installation."
  echo ""
  echo "    -n     Do not start Tika service after install, and do not abort on missing Java"
  echo ""
  echo " NOTE: Must be run as the root user"
  echo ""
} # end print_usage

print_error() {
  echo $1
  exit 1
}

# Locate *NIX distribution by looking for match from various detection strategies
# We start with /etc/os-release, as this will also work for Docker containers
for command in "grep -E \"^NAME=\" /etc/os-release" \
               "lsb_release -i" \
               "cat /proc/version" \
               "uname -a" ; do
    distro_string=$(eval $command 2>/dev/null)
    unset distro
    if [[ ${distro_string,,} == *"debian"* ]]; then
      distro=Debian
    elif [[ ${distro_string,,} == *"red hat"* ]]; then
      distro=RedHat
    elif [[ ${distro_string,,} == *"centos"* ]]; then
      distro=CentOS
    elif [[ ${distro_string,,} == *"ubuntu"* ]]; then
      distro=Ubuntu
    elif [[ ${distro_string,,} == *"suse"* ]]; then
      distro=SUSE
    elif [[ ${distro_string,,} == *"darwin"* ]]; then
      echo "Sorry, this script does not support macOS. You'll need to setup Tika as a service manually using the documentation provided in the Tika Reference Guide."
      echo "You could also try installing via Homebrew (http://brew.sh/), e.g. brew install tika"
      exit 1
    fi
    if [[ $distro ]] ; then break ; fi
done
if [[ ! $distro ]] ; then
  echo -e "\nERROR: Unable to auto-detect your *NIX distribution!\nYou'll need to setup Tika as a service manually using the documentation provided in the Tika Reference Guide.\n" 1>&2
  exit 1
fi

if [ -z "$1" ]; then
  print_usage "Must specify the path to the Tika installation archive, such as tika-server-2.0.0-SNAPSHOT-bin.tgz"
  exit 1
fi

TIKA_ARCHIVE=$1
if [ ! -f "$TIKA_ARCHIVE" ]; then
  print_usage "Specified Tika installation archive $TIKA_ARCHIVE not found!"
  exit 1
fi

# strip off path info
TIKA_INSTALL_FILE=${TIKA_ARCHIVE##*/}
is_tar=true
if [ ${TIKA_INSTALL_FILE: -4} == ".tgz" ]; then
  TIKA_DIR=${TIKA_INSTALL_FILE%.tgz}
elif [ ${TIKA_INSTALL_FILE: -4} == ".zip" ]; then
  TIKA_DIR=${TIKA_INSTALL_FILE%.zip}
  is_tar=false
else
  print_usage "Tika installation archive $TIKA_ARCHIVE is invalid, expected a .tgz or .zip file!"
  exit 1
fi

TIKA_START=true
if [ $# -gt 1 ]; then
  shift
  while true; do
    case $1 in
        -i)
            if [[ -z "$2" || "${2:0:1}" == "-" ]]; then
              print_usage "Directory path is required when using the $1 option!"
              exit 1
            fi
            TIKA_EXTRACT_DIR=$2
            shift 2
        ;;
        -d)
            if [[ -z "$2" || "${2:0:1}" == "-" ]]; then
              print_usage "Directory path is required when using the $1 option!"
              exit 1
            fi
            TIKA_VAR_DIR="$2"
            shift 2
        ;;
        -u)
            if [[ -z "$2" || "${2:0:1}" == "-" ]]; then
              print_usage "Username is required when using the $1 option!"
              exit 1
            fi
            TIKA_USER="$2"
            shift 2
        ;;
        -s)
            if [[ -z "$2" || "${2:0:1}" == "-" ]]; then
              print_usage "Service name is required when using the $1 option!"
              exit 1
            fi
            TIKA_SERVICE="$2"
            shift 2
        ;;
        -p)
            if [[ -z "$2" || "${2:0:1}" == "-" ]]; then
              print_usage "Port is required when using the $1 option!"
              exit 1
            fi
            TIKA_PORT="$2"
            shift 2
        ;;
        -f)
            TIKA_UPGRADE="YES"
            shift 1
        ;;
        -n)
            TIKA_START=false
            shift 1
        ;;
        -help|-usage)
            print_usage ""
            exit 0
        ;;
        --)
            shift
            break
        ;;
        *)
            if [ "$1" != "" ]; then
              print_usage "Unrecognized or misplaced argument: $1!"
              exit 1
            else
              break # out-of-args, stop looping
            fi
        ;;
    esac
  done
fi

# Test for availability of needed tools
if [[ $is_tar ]] ; then
  tar --version &>/dev/null     || print_error "Script requires the 'tar' command"
else
  unzip -hh &>/dev/null         || print_error "Script requires the 'unzip' command"
fi
if [[ $TIKA_START == "true" ]] ; then
  service --version &>/dev/null || service --help &>/dev/null || print_error "Script requires the 'service' command"
  java -version &>/dev/null     || print_error "Tika requires java, please install or set JAVA_HOME properly"
fi
lsof -h &>/dev/null             || echo "We recommend installing the 'lsof' command for more stable start/stop of Tika"


if [ -z "$TIKA_EXTRACT_DIR" ]; then
  TIKA_EXTRACT_DIR=/opt
fi

if [ ! -d "$TIKA_EXTRACT_DIR" ]; then
  print_usage "Installation directory $TIKA_EXTRACT_DIR not found! Please create it before running this script."
  exit 1
fi

if [ -z "$TIKA_SERVICE" ]; then
  TIKA_SERVICE=tika
fi

if [ -z "$TIKA_VAR_DIR" ]; then
  TIKA_VAR_DIR="/var/$TIKA_SERVICE"
fi

if [ -z "$TIKA_USER" ]; then
  TIKA_USER=tika
fi

if [ -z "$TIKA_PORT" ]; then
  TIKA_PORT=9998
fi

if [ -z "$TIKA_UPGRADE" ]; then
  TIKA_UPGRADE=NO
fi

if [ ! "$TIKA_UPGRADE" = "YES" ]; then
  if [ -f "/etc/init.d/$TIKA_SERVICE" ]; then
    print_usage "/etc/init.d/$TIKA_SERVICE already exists! Perhaps Tika is already setup as a service on this host? To upgrade Tika use the -f option."
    exit 1
  fi

  if [ -e "$TIKA_EXTRACT_DIR/$TIKA_SERVICE" ]; then
    print_usage "$TIKA_EXTRACT_DIR/$TIKA_SERVICE already exists! Please move this directory / link or choose a different service name using the -s option."
    exit 1
  fi
fi

# stop running instance
if [ -f "/etc/init.d/$TIKA_SERVICE" ]; then
  echo -e "\nStopping Tika instance if exists ...\n"
  service "$TIKA_SERVICE" stop
fi

# create user if not exists
tika_uid="`id -u "$TIKA_USER"`"
if [ $? -ne 0 ]; then
  echo "Creating new user: $TIKA_USER"
  if [ "$distro" == "RedHat" ] || [ "$distro" == "CentOS" ] ; then
    adduser --system -U -m --home-dir "$TIKA_VAR_DIR" "$TIKA_USER"
  elif [ "$distro" == "SUSE" ]; then
    useradd --system -U -m --home-dir "$TIKA_VAR_DIR" "$TIKA_USER"
  else
    adduser --system --shell /bin/bash --group --disabled-password --home "$TIKA_VAR_DIR" "$TIKA_USER"
  fi
fi

# extract
echo "Tika extract dir: $TIKA_EXTRACT_DIR"
echo "TIKA_DIR: $TIKA_DIR"

TIKA_INSTALL_DIR="$TIKA_EXTRACT_DIR/$TIKA_DIR"

echo "tika install dir: $TIKA_INSTALL_DIR "
if [ ! -d "$TIKA_INSTALL_DIR" ]; then

  echo -e "\nExtracting $TIKA_ARCHIVE to $TIKA_EXTRACT_DIR\n"

  if $is_tar ; then
    tar zxf "$TIKA_ARCHIVE" -C "$TIKA_EXTRACT_DIR"
  else
    unzip -q "$TIKA_ARCHIVE" -d "$TIKA_EXTRACT_DIR"
  fi

  if [ ! -d "$TIKA_INSTALL_DIR" ]; then
    echo -e "\nERROR: Expected directory $TIKA_INSTALL_DIR not found after extracting $TIKA_ARCHIVE ... script fails.\n" 1>&2
    exit 1
  fi

  chown -R root: "$TIKA_INSTALL_DIR"
  find "$TIKA_INSTALL_DIR" -type d -print0 | xargs -0 chmod 0755
  find "$TIKA_INSTALL_DIR" -type f -print0 | xargs -0 chmod 0644
  chmod -R 0755 "$TIKA_INSTALL_DIR/bin"
else
  echo -e "\nWARNING: $TIKA_INSTALL_DIR already exists! Skipping extract ...\n"
fi

# create a symlink for easier scripting
if [ -h "$TIKA_EXTRACT_DIR/$TIKA_SERVICE" ]; then
  echo -e "\nRemoving old symlink $TIKA_EXTRACT_DIR/$TIKA_SERVICE ...\n"
  rm "$TIKA_EXTRACT_DIR/$TIKA_SERVICE"
fi
if [ -e "$TIKA_EXTRACT_DIR/$TIKA_SERVICE" ]; then
  echo -e "\nWARNING: $TIKA_EXTRACT_DIR/$TIKA_SERVICE is not symlink! Skipping symlink update ...\n"
else
  echo -e "\nInstalling symlink $TIKA_EXTRACT_DIR/$TIKA_SERVICE -> $TIKA_INSTALL_DIR ...\n"
  ln -s "$TIKA_INSTALL_DIR" "$TIKA_EXTRACT_DIR/$TIKA_SERVICE"
fi

# install init.d script
echo -e "\nInstalling /etc/init.d/$TIKA_SERVICE script ...\n"
cp "$TIKA_INSTALL_DIR/bin/init.d/tika" "/etc/init.d/$TIKA_SERVICE"
chmod 0744 "/etc/init.d/$TIKA_SERVICE"
chown root: "/etc/init.d/$TIKA_SERVICE"
# do some basic variable substitution on the init.d script
sed_expr1="s#TIKA_INSTALL_DIR=.*#TIKA_INSTALL_DIR=\"$TIKA_EXTRACT_DIR/$TIKA_SERVICE\"#"
sed_expr2="s#TIKA_ENV=.*#TIKA_ENV=\"/etc/default/$TIKA_SERVICE.in.sh\"#"
sed_expr3="s#RUNAS=.*#RUNAS=\"$TIKA_USER\"#"
sed_expr4="s#Provides:.*#Provides: $TIKA_SERVICE#"
sed -i -e "$sed_expr1" -e "$sed_expr2" -e "$sed_expr3" -e "$sed_expr4" "/etc/init.d/$TIKA_SERVICE"

# install/move configuration
if [ ! -d /etc/default ]; then
  mkdir /etc/default
  chown root: /etc/default
  chmod 0755 /etc/default
fi
if [ -f "$TIKA_VAR_DIR/tika.in.sh" ]; then
  echo -e "\nMoving existing $TIKA_VAR_DIR/tika.in.sh to /etc/default/$TIKA_SERVICE.in.sh ...\n"
  mv "$TIKA_VAR_DIR/tika.in.sh" "/etc/default/$TIKA_SERVICE.in.sh"
elif [ -f "/etc/default/$TIKA_SERVICE.in.sh" ]; then
  echo -e "\n/etc/default/$TIKA_SERVICE.in.sh already exist. Skipping install ...\n"
else
  echo -e "\nInstalling /etc/default/$TIKA_SERVICE.in.sh ...\n"
  cp "$TIKA_INSTALL_DIR/bin/tika.in.sh" "/etc/default/$TIKA_SERVICE.in.sh"
  mv "$TIKA_INSTALL_DIR/bin/tika.in.sh" "$TIKA_INSTALL_DIR/bin/tika.in.sh.orig"
  echo "TIKA_PID_DIR=\"$TIKA_VAR_DIR\"
LOG4J_PROPS=\"$TIKA_VAR_DIR/log4j.properties\"
TIKA_LOGS_DIR=\"$TIKA_VAR_DIR/logs\"
TIKA_PORT=\"$TIKA_PORT\"
TIKA_FORKED_OPTS=\"$TIKA_FORKED_OPTS\"
" >> "/etc/default/$TIKA_SERVICE.in.sh"
fi
chown root:${TIKA_USER} "/etc/default/$TIKA_SERVICE.in.sh"
chmod 0640 "/etc/default/$TIKA_SERVICE.in.sh"

# install data directories and files
mkdir -p "$TIKA_VAR_DIR/logs"
chown -R "$TIKA_USER:" "$TIKA_VAR_DIR"
find "$TIKA_VAR_DIR" -type d -print0 | xargs -0 chmod 0750
find "$TIKA_VAR_DIR" -type f -print0 | xargs -0 chmod 0640  # currently no files exist in /var/tika

# configure autostart of service
if [[ "$distro" == "RedHat" || "$distro" == "CentOS" || "$distro" == "SUSE" ]]; then
  chkconfig "$TIKA_SERVICE" on
else
  update-rc.d "$TIKA_SERVICE" defaults
fi
echo "Service $TIKA_SERVICE installed."
echo "Customize Tika startup configuration in /etc/default/$TIKA_SERVICE.in.sh"

# start service
if [[ $TIKA_START == "true" ]] ; then
  service "$TIKA_SERVICE" start
  sleep 5
  service "$TIKA_SERVICE" status
else
  echo "Not starting Tika service (option -n given). Start manually with 'service $TIKA_SERVICE start'"
fi
