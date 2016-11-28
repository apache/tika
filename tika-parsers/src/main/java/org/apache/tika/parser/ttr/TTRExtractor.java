package org.apache.tika.parser.ttr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.util.StringTokenizer;

import org.htmlparser.Node;
import org.htmlparser.Parser;
import org.htmlparser.filters.TagNameFilter;
import org.htmlparser.nodes.TextNode;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;

import weka.clusterers.AbstractClusterer;
import weka.clusterers.ClusterEvaluation;
import weka.clusterers.Clusterer;
import weka.clusterers.Cobweb;
import weka.clusterers.EM;
import weka.clusterers.FarthestFirst;
import weka.clusterers.SimpleKMeans;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

/**
 * Uses the information from the HTML page to extract content.
 * 
 * @author weninger
 * 
 */
public class TTRExtractor {
	private double[] linkTagList;
	private double[] derivList;
	
	public TTRExtractor(){
		
	}

	public String[] extractText(String html, int k) {
		html = html.replaceAll("(?s)<!--.*?-->", "");
		html = html.replaceAll("(?s)<script.*?>.*?</script>", "");
		html = html.replaceAll("(?s)<SCRIPT.*?>.*?</SCRIPT>", "");
		html = html.replaceAll("(?s)<style.*?>.*?</style>", "");
		
		String[] result = new String[2];
		result[0] = "";
		result[1] = "";
		try {
		
			Parser p = new Parser(html);
			NodeList nl = p.parse(null);
			NodeList list = nl.extractAllNodesThatMatch(new TagNameFilter(
					"TITLE"), true);
			if (list.size() >= 1) {
				result[0] = list.elementAt(0).toPlainTextString();
			}

			BufferedReader br = new BufferedReader(
					new StringReader(nl.toHtml()));
			int numLines = 0;
			while (br.readLine() != null) {
				numLines++;
			}
			br.close();

			if (numLines == 0) {
				return result;
			}


			// numLines must be even!
			//if (numLines % 2 != 0) {
			//	numLines++;
			//}
			
			linkTagList = new double[numLines];

			String line;
			br = new BufferedReader(new StringReader(nl.toHtml()));
			for (int i = 0; i < linkTagList.length; i++) {
				line = br.readLine();
				line = line.trim();
				if (line.equals("")) {
					continue;
				}
				linkTagList[i] = getTextToTagRatio(line);
			}
			br.close();

			derivList = computeDerivs(linkTagList);
			linkTagList = smooth(linkTagList, 2);

			result[1] = computeWekaCluster(new SimpleKMeans(), nl, k);

		} catch (MalformedURLException e) {
			System.out.println(e.toString());
		} catch (IOException e) {
			System.out.println(e.toString());
		} catch (ParserException e) {
			System.out.println(e.toString());
			return result;
		}
		return result;
	}

	private double[] computeDerivs(double[] list) {
		double[] newList = new double[list.length];
		for(int i=0; i<list.length-1; i++){
			if(list.length-i > 3){
				double sum = list[i+1] + list[i+2] + list[i+3];
				double avg = sum/3.0;
				double deriv = list[i] - avg;
				newList[i] = Math.abs(deriv);
			}else if(list.length-i == 3){
				double sum = list[i+1] + list[i+2];
				double avg = sum/2.0;
				double deriv = list[i] - avg;
				newList[i] = Math.abs(deriv);
			}else if(list.length-i == 2){
				double sum = list[i+1];
				double avg = sum;
				double deriv = list[i] - avg;
				newList[i] = Math.abs(deriv);
			}
			//newList[i] = list[i] - list[i+1];
		}
		return newList;
	}

	public String removeAllTags(String html){
		try {
			Parser p = new Parser(html);
			NodeList nl = p.parse(null);
			return getText(nl, new StringBuffer());
		} catch (Exception e) {
			return html.replaceAll("<[^>]*>", "");
		}
	}
	
	public String getText(NodeList nl, StringBuffer sb){
		int i = 0;
		while (i < nl.size()) {
			Node next = nl.elementAt(i);
			if (next instanceof TextNode) {
				sb.append(((TextNode)next).getText()).append(" ");
			}else{
				if (next.getChildren() != null) {
					getText(next.getChildren(), sb);
				}
			}
			i++;
		}
		return sb.toString();
	}

	

	/**
	 * Compute the text to tag ratio of the line. Text is recorded as the number
	 * of characters in the String. Tag is recorded as the number of unique HTML
	 * tags that are found.
	 * 
	 * @param line
	 *            Single line of HTML text.
	 * @return The text to tag ratio of the given line. If no tags are found
	 *         then the text only is reported.
	 */
	private double getTextToTagRatio(String line) {
		int tag = 0;
		int text = 0;

		for (int i = 0; i >= 0 && i < line.length(); i++) {
			if (line.charAt(i) == '<') {
				tag++;
				i = line.indexOf('>', i);
				if (i == -1) {
					break;
				}
			} else if (tag == 0 && line.charAt(i) == '>') {
				text = 0;
				tag++;
			} else {
				text++;
			}
			
		}
		if (tag == 0) {
			tag = 1;
		}
		return (double) text / (double) tag;
	}

	/**
	 * Smoothes the array by taking the moving average of the array with a
	 * radius of f.
	 * 
	 * @param in
	 *            Array to be smoothed
	 * @param f
	 *            Radius to smooth by
	 * @return The smoothed array
	 */
	private double[] smooth(double in[], int f) {
		int size = in.length;
		double tmp[] = new double[size];

		for (int i = 0; i < size; i++) {
			int cnt = 0;
			int sum = 0;
			for (int j = (f * -1); j <= f; j++) {
				try {
					sum += in[i + j];
					cnt++;
				} catch (ArrayIndexOutOfBoundsException e) {
					// don't increment count
				}
			}
			tmp[i] = (double) sum / (double) cnt;
		}
		return tmp;
	}

	private String computeWekaCluster(Clusterer cl, NodeList nl,
			int numClusters) throws IOException {
		FastVector fv = new FastVector();
		fv.addElement(new Attribute("textToTagRatio"));
		fv.addElement(new Attribute("derivList"));
		Instances inst = new Instances("TEXT_TO_TAG", fv, 10);

		ClusterEvaluation eval = new ClusterEvaluation();
		for (int i = 0; i < linkTagList.length; i++) {
			Instance ins = new Instance(fv.size());
			ins.setValue(0, linkTagList[i]);
			ins.setValue(1, derivList[i]);
			inst.add(ins);
		}

		double bestClusterNum = 0;;


			try {
				if (cl instanceof EM) {
					((EM) cl).setMaxIterations(100);
					((EM) cl).setNumClusters(numClusters);
				} else if (cl instanceof SimpleKMeans) {
					((SimpleKMeans) cl).setNumClusters(numClusters);
				} else if (cl instanceof FarthestFirst) {
					((FarthestFirst) cl).setNumClusters(numClusters);
				} else if (cl instanceof AbstractClusterer) {
					
				} else if (cl instanceof Cobweb) {
					throw new Exception("Can't do Cobweb!");
					// can't do cobweb... no clustering input available
				} else {
					throw new Exception("What are you thinking?!");
					// you're on your own
				}
				cl.buildClusterer(inst);				
				eval.setClusterer(cl);
				eval.evaluateClusterer(inst);
				eval.getClusterAssignments();
				
	
				Instances cents = ((SimpleKMeans)cl).getClusterCentroids();
				bestClusterNum = 0;
				double lowest = Double.MAX_VALUE;
				
				
				for(int c=0; c<cents.numInstances(); c++){
					if(cents.instance(c).value(0) < lowest){
						lowest = cents.instance(c).value(0);
						bestClusterNum = c;
					}
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			}

		return clusterHtml(nl, eval.getClusterAssignments(), bestClusterNum);
	}

	private int getBestCluster(String results) {
		String working = results.substring(results.indexOf(" centers"), results
				.indexOf("Distortion:"));
		StringTokenizer st = new StringTokenizer(working);
		boolean start = false;
		double best = Double.MAX_VALUE;
		int bestCluster = 0;
		while(st.hasMoreTokens()){
			String tok = st.nextToken();
			if(tok.equals("Cluster")){
				start = true;
			}
			if(start){
				int clust = Integer.parseInt(st.nextToken());
				double x = Double.parseDouble(st.nextToken());
				double y = Double.parseDouble(st.nextToken());
				if(x*y < best){
					best = x*y;
					bestCluster = clust;
				}
				start = false;
			}
		}
		return bestCluster;
	}

	private String clusterHtml(NodeList nl, double[] assignments,
			double clusterNum) throws IOException {
		StringBuffer sb = new StringBuffer();
		BufferedReader br = new BufferedReader(new StringReader(nl.toHtml()));

		String line;
		try {
			for (int i = 0; (line = br.readLine()) != null; i++) {
				line = line.trim();
				if (line.equals("")) {
					continue;
				}

				if (clusterNum != assignments[i]) {
					sb.append(line).append('\n');
				}
			}
			br.close();
			return sb.toString();
		} catch (Exception e) {
			return "";
		}
	}

}
