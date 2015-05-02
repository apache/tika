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
package org.apache.tika.detect;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.ProbabilisticMimeDetectionSelector;

/**
 * A version of {@link DefaultDetector} for probabilistic mime
 *  detectors, which use statistical techniques to blend the
 *  results of differing underlying detectors when attempting
 *  to detect the type of a given file.
 * TODO Link to documentation on configuring these probabilities
 */
public class DefaultProbDetector extends CompositeDetector {
    private static final long serialVersionUID = -8836240060532323352L;

    private static List<Detector> getDefaultDetectors(
			ProbabilisticMimeDetectionSelector sel, ServiceLoader loader) {
		List<Detector> detectors = loader
				.loadStaticServiceProviders(Detector.class);
		Collections.sort(detectors, new Comparator<Detector>() {
			public int compare(Detector d1, Detector d2) {
				String n1 = d1.getClass().getName();
				String n2 = d2.getClass().getName();
				boolean t1 = n1.startsWith("org.apache.tika.");
				boolean t2 = n2.startsWith("org.apache.tika.");
				if (t1 == t2) {
					return n1.compareTo(n2);
				} else if (t1) {
					return 1;
				} else {
					return -1;
				}
			}
		});
	
		detectors.add(sel);
		return detectors;
	}

	private transient final ServiceLoader loader;

	public DefaultProbDetector(ProbabilisticMimeDetectionSelector sel,
			ServiceLoader loader) {
		super(sel.getMediaTypeRegistry(), getDefaultDetectors(sel, loader));
		this.loader = loader;
	}

	public DefaultProbDetector(ProbabilisticMimeDetectionSelector sel,
			ClassLoader loader) {
		this(sel, new ServiceLoader(loader));
	}

	public DefaultProbDetector(ClassLoader loader) {
		this(new ProbabilisticMimeDetectionSelector(), loader);
	}

	public DefaultProbDetector(MimeTypes types) {
		this(new ProbabilisticMimeDetectionSelector(types), new ServiceLoader());
	}

	public DefaultProbDetector() {
		this(MimeTypes.getDefaultMimeTypes());
	}

	@Override
	public List<Detector> getDetectors() {
		if (loader != null) {
			List<Detector> detectors = loader
					.loadDynamicServiceProviders(Detector.class);
			detectors.addAll(super.getDetectors());
			return detectors;
		} else {
			return super.getDetectors();
		}
	}
}
