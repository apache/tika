package org.apache.tika.parser.dwg;

import java.io.Serializable;


public class DWGParserConfig implements Serializable{
	
    private static final long serialVersionUID = -7623524257255755725L;
	private String dwgReadExecutable = "";
	private boolean cleanDwgReadOutput = true;
    private int cleanDwgReadOutputBatchSize = 10000000;
    // we need to remove non UTF chars and Nan's (dwread outputs these as nan)
    private String cleanDwgReadRegexToReplace = "[^\\x20-\\x7e]| nan,| nan ";
    private String cleanDwgReadReplaceWith = "";
    

    public String getDwgReadExecutable() {
		return dwgReadExecutable;
	}
	public boolean isCleanDwgReadOutput() {
		return cleanDwgReadOutput;
	}
	public int getCleanDwgReadOutputBatchSize() {
		return cleanDwgReadOutputBatchSize;
	}
	public String getCleanDwgReadRegexToReplace() {
		return cleanDwgReadRegexToReplace;
	}
	public String getCleanDwgReadReplaceWith() {
		return cleanDwgReadReplaceWith;
	}

    public void setDwgReadExecutable(String dwgReadExecutable) {
		this.dwgReadExecutable = dwgReadExecutable;
	}
	public void setCleanDwgReadOutput(boolean cleanDwgReadOutput) {
		this.cleanDwgReadOutput = cleanDwgReadOutput;
	}
	public void setCleanDwgReadOutputBatchSize(int cleanDwgReadOutputBatchSize) {
		this.cleanDwgReadOutputBatchSize = cleanDwgReadOutputBatchSize;
	}
	public void setCleanDwgReadRegexToReplace(String cleanDwgReadRegexToReplace) {
		this.cleanDwgReadRegexToReplace = cleanDwgReadRegexToReplace;
	}
	public void setCleanDwgReadReplaceWith(String cleanDwgReadReplaceWith) {
		this.cleanDwgReadReplaceWith = cleanDwgReadReplaceWith;
	}



}