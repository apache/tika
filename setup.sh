maven='3.8.7'
sudo apt install openjdk-17-jre-headless -y
cd ~
[ -d .maven ] || mkdir .maven 
cd .maven 
wget "https://dlcdn.apache.org/maven/maven-3/${maven}/binaries/apache-maven-${maven}-bin.tar.gz"
tar xzvf "apache-maven-${maven}-bin.tar.gz"
cd "apache-maven-${maven}/bin"
path=$(pwd)
toadd="\"${path}:\$PATH\""
echo "PATH=${toadd}" >> ~/.profile
source ~/.profile
