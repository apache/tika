/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.sax;

/**
 * Class that represents a standard reference.
 *
 */
public class StandardReference {
	private String mainOrganization;
	private String separator;
	private String secondOrganization;
	private String identifier;
	private double score;
	
	private StandardReference(String mainOrganizationAcronym, String separator, String secondOrganizationAcronym,
			String identifier, double score) {
		super();
		this.mainOrganization = mainOrganizationAcronym;
		this.separator = separator;
		this.secondOrganization = secondOrganizationAcronym;
		this.identifier = identifier;
		this.score = score;
	}

	public String getMainOrganizationAcronym() {
		return mainOrganization;
	}

	public void setMainOrganizationAcronym(String mainOrganizationAcronym) {
		this.mainOrganization = mainOrganizationAcronym;
	}

	public String getSeparator() {
		return separator;
	}

	public void setSeparator(String separator) {
		this.separator = separator;
	}

	public String getSecondOrganizationAcronym() {
		return secondOrganization;
	}

	public void setSecondOrganizationAcronym(String secondOrganizationAcronym) {
		this.secondOrganization = secondOrganizationAcronym;
	}

	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	public double getScore() {
		return score;
	}

	public void setScore(double score) {
		this.score = score;
	}

	@Override
	public String toString() {
		String standardReference = mainOrganization;
		
		if (separator != null && !separator.isEmpty()) {
			standardReference += separator + secondOrganization; 
		}
		
		standardReference += " " + identifier;
		
		return standardReference;
	}
	
	public static class StandardReferenceBuilder {
		private String mainOrganization;
		private String separator;
		private String secondOrganization;
		private String identifier;
		private double score;
		
		public StandardReferenceBuilder(String mainOrganization, String identifier) {
			this.mainOrganization = mainOrganization;
			this.separator = null;
			this.secondOrganization = null;
			this.identifier = identifier;
			this.score = 0;
		}
		
		public StandardReferenceBuilder setSecondOrganization(String separator, String secondOrganization) {
			this.separator = separator;
			this.secondOrganization = secondOrganization;
			return this;
		}
		
		public StandardReferenceBuilder setScore(double score) {
			this.score = score;
			return this;
		}
		
		public StandardReference build() {
			return new StandardReference(mainOrganization, separator, secondOrganization, identifier, score);
		}
	}
}
