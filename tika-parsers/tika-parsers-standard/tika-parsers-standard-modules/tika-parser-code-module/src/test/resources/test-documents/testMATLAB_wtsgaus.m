function b=wtsgaus(p,N)
% wtsgaus: weights for gaussian filter with specified frequency response 
% b=wtsgaus(p,N);
% Last revised 2003-3-14
%
% Weights for gaussian filter with specified frequency response 
% Specify te wavelength for the 0.50 respons, and the length of series, get
% the coefficients, or weights
%
%*** INPUT
%
% p (1 x 1)i  period (years) at which filter is to have amp frequency response of 0.5
% N (1 x 1)i  length of the time series (number of observations)
%
%*** OUTPUT
%
% b (1 x n)r  computed weights
% 
%
%*** REFERENCES
% 
% WMO 1966, p. 47
%
%*** UW FUNCTIONS CALLED -- NONE
%*** TOOLBOXES NEEDED -- stats
%
%*** NOTES
%
% Amplitude of frequency response drops to 0.50 at a wavelength of 
% about 6 standard deviations of the appropriate guassian curve
%
% N is used as an input to restict the possible filter size (number of weights) to no larger than the sample length

if p>N; 
    error(['Desired 50% period ' num2str(p) ' is greater than  the sample length ' int2str(N)]);
end;


% Check that period of 50% response at least 5 yr
if p<5;
   error('Period of 50% response must be at least 5 yr');
end;

sigma=p/6;  % Gaussian curve should have this standard deviation

x=-N:N;
b=normpdf(x/sigma,0,1);
bmax=max(b);
bkeep = b>=0.05*bmax; % keep weights at least 5% as big as central weight
b=b(bkeep);
b=b/sum(b); % force weights to sum to one

