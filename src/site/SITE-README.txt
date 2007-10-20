Here's how to update the live Tika website:
(http://incubator.apache.org/tika/)

1) Edit the content found here

2) Run "mvn site" to generate the website pages

3) Check the new content at target/site/index.html

4) Checkout https://svn.apache.org/repos/asf/incubator/tika/site
	and update the changed pages there
	
5) Commit your changes, both here and in the tika/site module

6) To activate the changes on the live website, login to 
	people.apache.org and run svn up in /www/incubator.apache.org/tika
	
7) That directory is replicated to the live website every few hours, so
	your changes can take some time to be live.
	
Easy and fun, isn't it? ;-)

This will get better once Tika graduates from the incubator.	 		 

