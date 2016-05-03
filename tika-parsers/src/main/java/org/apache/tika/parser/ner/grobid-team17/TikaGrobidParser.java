import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.tika.parser.ner.NERecogniser;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class TikaGrobidParser implements NERecogniser {
	
	private static final String GROBID_REST_HOST_URL = "http://localhost:8080";
	private String requestURL;
	private boolean availableFlag = false;
	
	public static final Set<String> entityTypes = new HashSet<String>();
	
	//Constructor
	public TikaGrobidParser(){
		this.requestURL = GROBID_REST_HOST_URL + "/processQuantityText ";
		
		entityTypes.add("Raw_Values");
		entityTypes.add("Normalized_Quantities");
		entityTypes.add("Raw_Units");
		entityTypes.add("Normalized_Unit");
		
	}

	@Override
	public Set<String> getEntityTypes() {
		return entityTypes;
	}

	@Override
	public boolean isAvailable() {
		try {
			URL obj = new URL(GROBID_REST_HOST_URL);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();
			con.setRequestMethod("GET");
			// Send request
			con.setDoOutput(true);
			int responseCode = con.getResponseCode();
			
			if(responseCode == 200){
				this.availableFlag = true; 
			}
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return availableFlag;
	}
	
	public JSONObject getJsonObject(String jsonString){
		JSONParser parser = new JSONParser();
		JSONObject jsonObj = new JSONObject();
		//Parse jsonString to create json object 
		try {
			jsonObj = (JSONObject) parser.parse(jsonString);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return jsonObj;
	}
	
	public Map<String, Set<String>> ResultBuilderUtil(String jsonString) throws ParseException{
		Map<String, Set<String>> result = new HashMap<String,Set<String>>();
		
		Set<String> rawValueSet = new HashSet<String>();
		Set<String> normalizedUnitSet = new HashSet<String>();
		Set<String> normalizedQuantitySet = new HashSet<String>();
		Set<String> rawUnitSet = new HashSet<String>();
		
		//Parse jsonString to create json object 
		JSONObject jsonObj = this.getJsonObject(jsonString);
		
		//get the size of total measurement quantities
		JSONArray measurementArray = (JSONArray)jsonObj.get("measurements");
		int measurementSize = measurementArray.size();
		
		//Building the a new Json here
		for(int i=0;i<measurementSize;i++){
			JSONObject uniqueObject = (JSONObject) this.getJsonObject(measurementArray.get(i).toString()).get("quantity");
			
			if(uniqueObject != null){
				
				if(uniqueObject.containsKey("rawValue")){
					String rawValue = (String) this.getJsonObject(uniqueObject.toString()).get("rawValue");
					rawValueSet.add(rawValue);
				}
				
				if(uniqueObject.containsKey("normalizedQuantity")){
					String normalizedQuantity = (String) this.getJsonObject(uniqueObject.toString()).get("normalizedQuantity").toString();
					normalizedQuantitySet.add(normalizedQuantity);
				}
				
				if(uniqueObject.containsKey("rawUnit")){
					JSONObject rawUnit = (JSONObject) this.getJsonObject(uniqueObject.toString()).get("rawUnit");
					rawUnitSet.add(rawUnit.get("name").toString());
				}
				
				if(uniqueObject.containsKey("normalizedUnit")){
					JSONObject normalizedUnit = (JSONObject) this.getJsonObject(uniqueObject.toString()).get("normalizedUnit");
					normalizedUnitSet.add(normalizedUnit.get("name").toString());
				}
			}
		}
		result.put("Raw_Values",rawValueSet);
		result.put("Normalized_Quantities", normalizedQuantitySet);
		result.put("Raw_Units", rawUnitSet);
		result.put("Normalized_Unit", normalizedUnitSet);
	return result;
	}
	
	@Override
	public Map<String, Set<String>> recognise(String data) {
		Map<String, Set<String>> result = null;
		
		try {
			URL obj = new URL(requestURL);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();
			
			con.setRequestMethod("POST");
			
			String urlParameters = "text=" + data;
			
			// Send post request
			con.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();
			
			int responseCode = con.getResponseCode();
			System.out.println("\nSending 'POST' request to URL : " + requestURL);
			System.out.println("Post parameters : " + urlParameters);
			System.out.println("Response Code : " + responseCode);
			
			if(responseCode == 200){
				BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				String inputLine;
				StringBuffer response = new StringBuffer();
	
				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
				in.close();
				result = ResultBuilderUtil(response.toString());
				
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e){
			
		}
		return result;
	}

}
