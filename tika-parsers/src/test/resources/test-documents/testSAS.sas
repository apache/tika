data testing;
begin=0;
end=10;
msg="This is row %x of %y";
do i = begin to end by 1;
drop msg begin end i;
recnum=i;
label=tranwrd(tranwrd(msg,"%x",i),"%y",end);
output;
end;
run;

libname out          '/home/tika/testing/sas';
libname outxpt XPORT '/home/tika/testing/sas/testing.xpt';
libname outv6 v6     '/home/tika/testing/sas';
libname outxml xmlv2 '/home/tika/testing/sas';

data out.testing;
set testing;
run;
data outv6.testv6;
set testing;
run;
data outxml.testxml;
set testing;
run;
proc copy in=out out=outxpt;
select testing;
run;


proc print data=testing;
run;
