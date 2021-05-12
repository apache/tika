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
/* Days / Seconds since Epoc / Seconds since midnight */
format date ddmmyyd10.;
format datetime datetime.;
format time time.;
date=i**4;
datetime=10**i;
time=3**i;
output;
end;
label recnum="Record Number"
      square="Square of the Record Number"
	  desc="Description of the Row"
	  pctdone="Percent Done"
	  pctincr="Percent Increment";
run;

%let outpath = /home/tika/testing/sas;
libname out          "&outpath";
libname outxpt XPORT "&outpath./testing.xpt";
libname outv6 v6     "&outpath";
libname outxml xmlv2 "&outpath";

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

proc export data=testing label
  outfile="&outpath./testing.csv"
  dbms=CSV REPLACE;
putnames=yes;
run;

/* Due to SAS Limitations, you will need to manually */
/* style the % and Date/Datetime columns in Excel */
/* You will also need to save-as XLSB to generate that */
proc export data=testing label 
  outfile="&outpath./testing.xls"
  dbms=XLS;
run;
proc export data=testing label
  outfile="&outpath./testing.xlsx"
  dbms=XLSX;
run;
