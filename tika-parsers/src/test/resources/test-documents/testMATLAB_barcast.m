%% CONTROL CODE FOR FULLY BAYESIAN SPATIO-TEMPORAL TEMPERATURE RECONSTRUCTION
%EVERYTHING IS MODULAR TO ALLOW FOR EASY DEBUGGING AND ADAPTATION
% _vNewModel_Oct08: change the formalism to reflect new model (Beta_1 now
% normal). Allows for multiple proxies
clear all; close all;
%SET MATLAB'S CURRENT DIRECTORY TO HERE. 
% set the priors and the inital values for the MCMC sampler
Prior_pars_vNewModel
Initial_par_vals_vNewModel
%% Set the seed of the random number generators
randn('state', sum((1000+600)*clock))
rand('state', sum((1000+800)*clock))

%% load the data
cd TestData
load BARCAST_INPUT_vNewMeth1
%break it apart
Locs=BARCAST_INPUT.Master_Locs;
N_Locs=length(Locs(:,1)); %Number of locations:
timeline=[BARCAST_INPUT.Data_timeline(1)-1, BARCAST_INPUT.Data_timeline];
N_Times=length(timeline)-1; %Number of DATA times
loc_areas=BARCAST_INPUT.Areas;
Inds_GridLocs_Central=BARCAST_INPUT.Inds_Central;

%get the number of proxy types:
N_PT=length(fieldnames(BARCAST_INPUT))-5;

%stack the three data matrices, one on top of the other
%the first N_Locs ROWS are the Inst, the next N_Locs ROWS the first proxy
%type, the next the third. . . . .. Each column a year. The first
%corresponds to the SECOND entry in timeline. 
Data_ALL=BARCAST_INPUT.Inst_Data;
for kk=1:1:N_PT
    tp=eval(['BARCAST_INPUT.Prox_Data', num2str(kk)]);
    Data_ALL=[Data_ALL; tp];
end

% % % % All_locs_wInd=BARCAST_INPUT.All_locs_wInd;
% % % % lon_lat_area=BARCAST_INPUT.lon_lat_area;
% % % % DATA_Mat=BARCAST_INPUT.DATA_Mat;
% % % % DATA_Mat_locs=BARCAST_INPUT.DATA_Mat_locs;
% % % % Inds_GridLocs_Central=BARCAST_INPUT.Inds_GridLocs_Central;
% % % % timeline=BARCAST_INPUT.timeline;
% % % % clear BARCAST_INPUT

%Priors and MH jumping parameters, from Prior_pars_vNewModel
load PRIORS_vNewMeth1
load MHpars_vNewMeth1
%Initial values from Initial_par_vals_vNewModel
load INITIAL_VALS_vNewMeth1

%The Order of THE SCALAR parameters WILL ALWAYS thus:
%1 = alpha, the AR(1) coefficient
%2 = mu, the constant par in the linear mean of the AR(1) process
%3 = sigma2, the partial sill in the spatial covariance matrix
%4 = phi, the range parameter in the spatial covariance matrix
%5 = tau2_I, the Inst measurement error
%6 = tau2_P, the measurement error, first PROX type
%7 = Beta_1, the scaling par in the  first P observation equation
%8 = Beta_0, the additive par in the first P observation equation
%and, if there is second proxy type
%9  = tau2_P_2, the measurement error, second PROX type
%10 = Beta_1, the scaling par in the  second P observation equation
%11 = Beta_0, the additive par in the second P observation equation
%and, if there is third proxy type . . . . 

%A NOTE ON GAMMA NOTATION. WE USE THE NOTATION OF Gelman et al, "Bayesian
%Data Analysis", WHERE GAMMA PARAMETERS ALPHA, BETA)==(SHAPE, INVERSE SCALE). 
%THE RANDRAW.M CODE USES (A,B)==(SHAPE, SCALE), AND THE CALL IS RANDRAW('GAMMA', [M,B,A], SAMPLESIZE), 
%WHERE M IS THE LOCATION (NOT NEEDED). SO IN THE NOTATION OF GELMAN ET AT, THE CALL IS
%RANDRAW('GAMMA', [0,1/BETA,ALPHA], SAMPLESIZE). 
%For example,
%RANDRAW('GAMMA', [0,1/PRIORS.sigma2(2),PRIORS.sigma2(1)], 1), AND ETC. 

%switch back tot he main directory
cd ..
%% SET a few parameters
%Number of iterations of the complete sampler
Sampler_Its=2000;

%Number of times to update only the temperature array before beginning to
%update the other parameters
pre_Sampler_Its=500; 


%% Areal weights vector for averaging the temperatures at each year
%note that some of the elments of the temeprature are given 0 weight -
%outside the prediction bounds. This is based on an input of the area of
%each gridbox
SpaceWeight=loc_areas/sum(loc_areas);
%and for the central region/region of interest
Areas_Central=zeros(1,N_Locs);
Areas_Central(Inds_GridLocs_Central)=loc_areas(Inds_GridLocs_Central);
SpaceWeight_Central=Areas_Central/sum(Areas_Central);

%(In some applications, the goal might be to estimate the block average
%over a subset of the locations in the reconstruction. For example, the
%goal might be to reconstruct temperatures in Maine, but proxy records from
%NH are incldued in the analysis, as they help to constrain temperatures in
%Maine. SO some of the weights are, in this case, set to zero). 


%% CALCULATE FIXED QUANTITIES (DO NOT DEPEND ON UNKOWN PARAMETERS)

%The matrix of distances between every possible pair of points, (I,P,R)
All_DistMat=EarthDistances(Locs);

%The H(t) selection matrix. 
%Basically, H(t) tells us which Inst and prox
%locations have measurements for a given year. So: define H(t) for each
%year as an indicator vector, and thus HH a matrix such that each column is
%the indicator vector for that year. In other words, this is the complete
%indicator matrix for the presence of data::
%1=YES Measurement;
%0=NO  Measurement
%Simply a ZERO wherever there is a NaN in Data_ALL, and a ONE whereever
%this is a value
HH_SelectMat=ones(size(Data_ALL))-isnan(Data_ALL);

%The total number of Inst/Prox Observations are needed for several
%conditional posteriors, and can be calculated from the HH_SelectMat:
M_InstProx=NaN(1+N_PT,1);
%vectot: first the total number of inst obsm then the total number of each
%prox type, in order.
%Inst:
M_InstProx(1)=sum(sum(HH_SelectMat(1:1:N_Locs, :)));
%Prox:
for kk=1:1:N_PT
    M_InstProx(kk+1)=sum(sum(HH_SelectMat(kk*N_Locs+1:1:(kk+1)*N_Locs, :)));
end

%% Set the initial values of the Field matrix and Current Parameter Vector
% These will be updated and then saved at each iteration of the sampler.
% They are initially filled with the values from INITIAL_VALS.
% Paramter/field values at each step of the gibbs sampler are taken from
% these objects, and new draws override the current entries. This ensures
% that each step of the Gibbs sampler is ALWAYS using the most recent set of ALL 
% parameters, without having to deal with +/-1 indices.

%Array of the estimated true temperature values, set to the initial values:  
Temperature_MCMC_Sampler=INITIAL_VALS.Temperature;
%Order: All I, P with locs common to I, Rest of the P, R.
%In other words, ordered the same as InstProx_locs, then with Rand_locs
%added on
%note that
%[Inst_locs; Prox_locs] = InstProx_locs([Inst_inds,Prox_inds],:)
%SO: Temperature_MCMC_Sampler([Inst_inds,Prox_inds], KK) extracts the
%elements that can be compared to the corresponding time of DATA_Mat

% Current values of the scalar parameters
INITIAL_SCALAR_VALS=rmfield(INITIAL_VALS, 'Temperature');
CURRENT_PARS=cell2mat(struct2cell(INITIAL_SCALAR_VALS));

% OR LOAD TRUE VALUES - FOR TESTING
% load TestData\Pars_TRUE
% CURRENT_PARS=Pars_TRUE';
% 
% load TestData\TrueTemps_v1
% Temperature_MCMC_Sampler=Temperature_Matrix;

%% DEFINE EMPTY MATRICES that will be filled with the sampler
%DEFINE the empty parameter matrix:
N_Pars=length(CURRENT_PARS);
Paramters_MCMC_Samples=NaN(N_Pars, Sampler_Its);
%The empty matrix of the samples of the blockaverage timeseries:
BlockAve_MCMC_Samples=NaN(N_Times+1, pre_Sampler_Its+Sampler_Its);
%and the central/target portion
BlockAve_Central_MCMC_Samples=NaN(N_Times+1, pre_Sampler_Its+Sampler_Its);
%NOTE the initial values of the parameters, field, and block averages will
%NOT be saved. So the first item in all matrices/arrays are the results
%after the first iteration of the sampler

%IN this case, as the amount of data is small, we are able to deal
%with the whole array of space time draws. In applications with larger
%data, this is not possible (memory overflow). 
Temperature_ARRAY=NaN(N_Locs, N_Times+1, pre_Sampler_Its+Sampler_Its);


%% CALCULATE PARAMETER DEPENDENT QUANTITIES
%that are used several times in the sampler
%
%The idea: calculate the quantities with the initial parameter values, then
%update as soon as possible, leaving the variablle name the same
%
%calculate the initial spatial correlation matrix, and its inverse
%these are needed several times.
%AS SOON as phi is updated, this is updated, ensuring that the
%correlation matrix and its inverse are always up to date, regardless of
%the order of the sampling below.
CURRENT_spatial_corr_mat=exp(-CURRENT_PARS(4)*All_DistMat);
CURRENT_inv_spatial_corr_mat=inv(CURRENT_spatial_corr_mat);

%% To speed up the code 
%1. Find the UNIQUE missing data patterns, number them.
%2. Index each year by the missing data pattern.
%3. For each missing data pattern, calculate the inverse and square root of
%the conditional posterior covariance of a T_k, and stack them
%4. Rewrite the T_k_Updater to simply call these matrices. 
%This reduces the number of matrix inversions for each FULL iteration of
%the sampler to the number of UNIQUE data patterns, and reduces the number
%for the pre iterations to 2. 

U_Patterns=unique(HH_SelectMat', 'rows');
%create an index vector that gices, for each year, the number of the
%corresponding pattern in U_Patterns
%Basically - HH_SelectMat can be represented by U_Patterns and this index vector:
Pattern_by_Year=NaN(N_Times,1);
for kk=1:1:length(U_Patterns(:,1));
    dummy=ismember(HH_SelectMat', U_Patterns(kk,:), 'rows');
    Pattern_by_Year(find(dummy==1))=kk;
end

%Input the CURRENT_PARS vector and etc into Covariance_Patterns, which returns two 3d
%arrays: the covariance amtrix for each missing data patter (for
%the mean calculation) and the squre root of the covariance matrix (to make
%the draw). 
[CURRENT_COV_ARRAY, CURRENT_SQRT_COV_ARRAY]=Covariance_Patterns(U_Patterns, CURRENT_PARS, CURRENT_inv_spatial_corr_mat, N_Locs, N_PT);



%% In an attempt to speed convergence of the variance paramters
% we will uptate only the true temperature array for a number of
% iterations, and then add the updating of the other parameters. This is to
% prevent the model from requiring large variances to fit the observations
% to the data.
%timertimer=NaN;
for samples=1:1:pre_Sampler_Its
    tic;
    %% SAMPLE T(0): True temperature the year before the first measurement.
    Temperature_MCMC_Sampler(:,1)=T_0_Updater_vNM(PRIORS.T_0, Temperature_MCMC_Sampler(:,2), CURRENT_PARS, CURRENT_inv_spatial_corr_mat);
    
    %% SAMPLE T(1), . . ., T(last-1). Recall that the T matrix starts at time=0, while the W matrix starts at time=1
    for Tm=2:1:N_Times
        Temperature_MCMC_Sampler(:,Tm)=T_k_Updater_vFAST(Temperature_MCMC_Sampler(:, Tm-1), Temperature_MCMC_Sampler(:,Tm+1), Data_ALL(:,Tm-1), CURRENT_PARS, U_Patterns(Pattern_by_Year(Tm-1),:),CURRENT_COV_ARRAY(:,:,Pattern_by_Year(Tm-1)),CURRENT_SQRT_COV_ARRAY(:,:,Pattern_by_Year(Tm-1)),CURRENT_inv_spatial_corr_mat, N_Locs, N_PT);
    end
    %This is a SLOW step, because it is actually N_Times-1 steps. . . 

    %% SAMPLE T(last)
    Temperature_MCMC_Sampler(:,N_Times+1)=T_last_Updater_vNM(Temperature_MCMC_Sampler(:, N_Times), Data_ALL(:,N_Times), HH_SelectMat(:, N_Times), CURRENT_PARS, CURRENT_inv_spatial_corr_mat, N_Locs, N_PT);
           
    %% Fill in the next iteration of the BlockAve_MCMC_Samples matrix: 
	BlockAve_MCMC_Samples(:,samples)=(SpaceWeight*Temperature_MCMC_Sampler)';
    BlockAve_Central_MCMC_Samples(:, samples)=(SpaceWeight_Central*Temperature_MCMC_Sampler)';
    %Fill in the next slice of the space-time field draw array
    Temperature_ARRAY(:,:,samples)=Temperature_MCMC_Sampler;
    
    %save the current draw of the space-time temp matrix
    %save(['TestData\FieldDraws\Temp_MCMC_vNM_Test_PreStep' num2str(samples)],'Temperature_MCMC_Sampler');

    timertimer=toc;
	disp(['Working on pre-MCMC iteration ', num2str(samples), ' of ', num2str(pre_Sampler_Its), '. Last iteration took ', num2str(timertimer), ' seconds.'])

end

timertimer=NaN;
%% RUN THE SAMPLER
for samples=1:1:Sampler_Its
    
    tic
    %% SAMPLE T(0): True temperature the year before the first measurement.
    Temperature_MCMC_Sampler(:,1)=T_0_Updater_vNM(PRIORS.T_0, Temperature_MCMC_Sampler(:,2), CURRENT_PARS, CURRENT_inv_spatial_corr_mat);
    
    %% SAMPLE T(1), . . ., T(last-1). Recall that the T matrix starts at time=0, while the W matrix starts at time=1
    for Tm=2:1:N_Times
        Temperature_MCMC_Sampler(:,Tm)=T_k_Updater_vFAST(Temperature_MCMC_Sampler(:, Tm-1), Temperature_MCMC_Sampler(:,Tm+1), Data_ALL(:,Tm-1), CURRENT_PARS, U_Patterns(Pattern_by_Year(Tm-1),:),CURRENT_COV_ARRAY(:,:,Pattern_by_Year(Tm-1)),CURRENT_SQRT_COV_ARRAY(:,:,Pattern_by_Year(Tm-1)),CURRENT_inv_spatial_corr_mat, N_Locs, N_PT);
    end
    %This is a SLOW step, because it is actually N_Times-1 steps. . . 

    %% SAMPLE T(last)
    Temperature_MCMC_Sampler(:,N_Times+1)=T_last_Updater_vNM(Temperature_MCMC_Sampler(:, N_Times), Data_ALL(:,N_Times), HH_SelectMat(:, N_Times), CURRENT_PARS, CURRENT_inv_spatial_corr_mat, N_Locs, N_PT);
    
    %% SAMPLE AR(1) coefficient    
    New_Alpha=Alpha_Updater_vNM(PRIORS.alpha, Temperature_MCMC_Sampler, CURRENT_PARS, CURRENT_inv_spatial_corr_mat);
    CURRENT_PARS(1)=New_Alpha;
    clear New_Alpha

    %% SAMPLE AR(1) mean constant parameter, mu:
    New_mu=Mu_Updater_vNM(PRIORS.mu, Temperature_MCMC_Sampler, CURRENT_PARS, CURRENT_inv_spatial_corr_mat);
    CURRENT_PARS(2)=New_mu;
    clear New_AR_mean_mu
    
    %% SAMPLE Partial Sill of the spatial covaraince martrix
    New_sigma2=Sigma2_Updater_vNM(PRIORS.sigma2, Temperature_MCMC_Sampler, CURRENT_PARS, CURRENT_inv_spatial_corr_mat);
    %ARTIFICIALLY put a cieling at, say, 5.
    %CHECK a posterior that, one the algorithm has converged, ALL draws are
    %lower than this. 
    CURRENT_PARS(3)=min(5, New_sigma2);
    clear New_sigma2
	
    %% SAMPLE Range Parameter of the spatial covaraince martrix (METROPOLIS)
    % This also updates the spatial corelation matrix and its inverse
    [New_phi, New_scm, New_iscm]=Phi_Updater_vNM(PRIORS.phi, Temperature_MCMC_Sampler, CURRENT_PARS, CURRENT_spatial_corr_mat, CURRENT_inv_spatial_corr_mat, All_DistMat, MHpars.log_phi);
    CURRENT_PARS(4)=New_phi;
    CURRENT_spatial_corr_mat=New_scm;
    CURRENT_inv_spatial_corr_mat=New_iscm;
    clear New_phi New_iscm New_scm
    
    %% SAMPLE Instrumental measurement error
    New_tau2_I=tau2_I_Updater_vNM(PRIORS.tau2_I, Temperature_MCMC_Sampler, Data_ALL, N_Locs, M_InstProx(1));
    %ARTIFICIALLY put a cieling at, say, 5.
    %CHECK a posterior that, one the algorithm has converged, ALL draws are
    %lower than this. 
    CURRENT_PARS(5)=min(5, New_tau2_I);
    clear New_tau2_I
    
    
    
    %% NEED TO LOOP THE SAMPLING OF THESE THREE PARAMETERS
    for Pnum=1:1:N_PT
        %curtail the CURRENT_PARS vector to only include the pars for one
        %proxy type at a time:
        CURRENT_PARS_Brief=[CURRENT_PARS(1:1:5); CURRENT_PARS([6:1:8]+(Pnum-1)*3)];
        %Similarily exract each type of proxy data:
        Prox_Data_Brief=eval(['BARCAST_INPUT.Prox_Data', num2str(Pnum)]);

        %% SAMPLE Proxy measurement error
        New_tau2_P=tau2_P_Updater_vNM(eval(['PRIORS.tau2_P_', num2str(Pnum)]), Temperature_MCMC_Sampler, Prox_Data_Brief, CURRENT_PARS_Brief, M_InstProx(Pnum+1));
        %ARTIFICIALLY put a cieling at, say, 50.
        %CHECK a posterior that, one the algorithm has converged, ALL draws are
        %lower than this.
        CURRENT_PARS_Brief(6)=min(10, New_tau2_P);
        clear New_tau2_P

        %% SAMPLE Scaling constant in the proxy observation equation
        New_beta_1=Beta_1_Updater_vNM(eval(['PRIORS.Beta_1_', num2str(Pnum)]), Temperature_MCMC_Sampler, Prox_Data_Brief, CURRENT_PARS_Brief);
        CURRENT_PARS_Brief(7)=New_beta_1;
        clear New_beta_1

        %% SAMPLE Additive constant in the proxy observation equation
        New_Beta_0=Beta_0_Updater_vNM(eval(['PRIORS.Beta_0_', num2str(Pnum)]), Temperature_MCMC_Sampler, Prox_Data_Brief, CURRENT_PARS_Brief, M_InstProx(Pnum+1));
        CURRENT_PARS_Brief(8)=New_Beta_0;
        clear New_Beta_0

        CURRENT_PARS([6:1:8]+(Pnum-1)*3)=CURRENT_PARS_Brief(6:1:8);

    end
    
    %% UPDATE the covariance arrays used in the T_k_Updater step
    [CURRENT_COV_ARRAY, CURRENT_SQRT_COV_ARRAY]=Covariance_Patterns(U_Patterns, CURRENT_PARS, CURRENT_inv_spatial_corr_mat, N_Locs, N_PT);

    
    %% UPDATE THE VARIOUS MATRICES, SAVE CURRENT TEMPERTAURE MATRIX
    %update the Paramters_MCMC_Samples matrix:
    Paramters_MCMC_Samples(:, samples)=CURRENT_PARS;
    %CURRENT_PARS is not cleared: it is, after all, the current parameter
    %vector. 
    
    %Fill in the next iteration of the BlockAve_MCMC_Samples matrix:
    BlockAve_MCMC_Samples(:, pre_Sampler_Its+samples)=(SpaceWeight*Temperature_MCMC_Sampler)';
    BlockAve_Central_MCMC_Samples(:, pre_Sampler_Its+samples)=(SpaceWeight_Central*Temperature_MCMC_Sampler)';

    %add the new draw of the space-time temp matrix
    Temperature_ARRAY(:,:,pre_Sampler_Its+samples)=Temperature_MCMC_Sampler;
    %save the current draw of the space-time temp matrix
    %save(['TestData\FieldDraws\Temp_MCMC_vNM_Test_Step' num2str(samples)],'Temperature_MCMC_Sampler');    
    
    %SAVE the matrix of parameter vector draws and the matrix of block
    %average vectors. (This way, even if the code is stopped prematurely,
    %we get something)
    %cd TestData
    %cd FieldDraws
    %save TestData\FieldDraws\Paramters_MCMC_Samples_vNM Paramters_MCMC_Samples 
    %save TestData\FieldDraws\Temperature_ARRAY_vNM Temperature_ARRAY
    %save TestData\FieldDraws\BlockAve_MCMC_Samples_vNM BlockAve_MCMC_Samples
    %save TestData\FieldDraws\BlockAve_Central_MCMC_Samples_vNM BlockAve_Central_MCMC_Samples
    %and back
    %cd ..
    %cd ..
    timertimer=toc;
    disp(['Finished MCMC iteration ', num2str(samples), ' of ', num2str(Sampler_Its), '. Last iteration took ', num2str(timertimer), ' seconds.'])
end

%% SAVE the matrix of parameter vector draws and the matrix of block
%average vectors. 
cd TestData
cd FieldDraws
    save Paramters_MCMC_Samples_vNM Paramters_MCMC_Samples 
    save Temperature_ARRAY_vNM Temperature_ARRAY
    save BlockAve_MCMC_Samples_vNM BlockAve_MCMC_Samples
    save BlockAve_Central_MCMC_Samples_vNM BlockAve_Central_MCMC_Samples
%and back
cd ..
cd ..
