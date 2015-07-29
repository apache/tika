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
package org.apache.tika.parser.journal;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.grobid.core.data.BiblioItem;

public class GrobidHeaderMetadata {
	

	private Map<String, String> headerMetadata;
	
	public void generateHeaderMetada(BiblioItem resHeader) {
		headerMetadata = new HashMap<String, String>();
		try {
			BeanInfo info = Introspector.getBeanInfo(BiblioItem.class);
			
			for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
				Method m = pd.getReadMethod();
				if( m!= null) {
					Object value = m.invoke(resHeader);
					if(value != null){
						headerMetadata.put(GrobidConfig.HEADER_METADATA_PREFIX +  m.getName().replace("get", ""), "" +value);
					}
					
				}
			}
		} catch (IntrospectionException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
    
	public Map<String, String> getHeaderMetadata() {
		return headerMetadata;
	}
	
	public void setHeaderMetadata(Map<String, String> headerMetadata) {
		this.headerMetadata = headerMetadata;
	}
}
