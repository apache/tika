data testing;
begin=0;
end=10;
msg="This is row %x of %y";
do i = begin to end by 1;
drop msg begin end i;
recnum=i;
square=i*i;
desc=tranwrd(tranwrd(msg,"%x",i),"%y",end);
format pctdone percent8.0;
format pctincr percent7.1;
pctdone=divide(i,end);
pctincr=divide(i-1,i);
format date ddmmyyd10.;
format datetime datetime.;
date=i**4;
datetime=10**i;
output;
end;
label recnum="Record Number"
      square="Square of the Record Number"
	  desc="Description of the Row"
	  pctdone="Percent Done"
	  pctincr="Percent Increment";
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

