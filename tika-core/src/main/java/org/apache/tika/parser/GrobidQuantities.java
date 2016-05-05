import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class GrobidQuantities {
	static FileWriter writer;
	static FileWriter jsonwrite;

	public static void parse(String args) throws IOException, InterruptedException, ParseException {
		File directory = new File(args);
		// writer=new FileWriter("/home/adeshgupta/geotopic.csv");
		// writer.append("Latitude,Longitude,Name\n");
		int my_cnt = 0;
		System.out.println(args[0]);
		for (File file : directory.listFiles()) 
		{

			System.out.println("File: "+file.getAbsolutePath());
			
			
			/* if(my_cnt<=5020) { 
				 System.out.println(my_cnt); my_cnt++;
			 continue; }*/
			 
			/*String sub = file.getName().substring(0, 2);
			if (!sub.equals("00") && !sub.equals("01") && !sub.equals("02") && !sub.equals("03") && !sub.equals("04")
					&& !sub.equals("05") && !sub.equals("06") && !sub.equals("07") && !sub.equals("08")
					&& !sub.equals("09") && !sub.equals("0A") && !sub.equals("0B") && !sub.equals("0C")
					&& !sub.equals("0D") && !sub.equals("0E") && !sub.equals("0F") && !sub.equals("10")
					&& !sub.equals("11") && !sub.equals("12") && !sub.equals("13") && !sub.equals("14")
					&& !sub.equals("15") && !sub.equals("16") && !sub.equals("17") && !sub.equals("18")
					&& !sub.equals("19") && !sub.equals("1A") && !sub.equals("1B") && !sub.equals("1C")
					&& !sub.equals("1D") && !sub.equals("1E") && !sub.equals("1F") && !sub.equals("20")) {
				System.out.println("i am breaking as files done");
				break;
			}*/

			System.out.println(file.getName());
/*			String cmd = "java -jar /home/adeshgupta/workspace/Assignment2/tika-app-1.13-SNAPSHOT.jar -x "
					+ "/media/adeshgupta/Seagate\\ Expansion\\ Drive/data/text/x-python/" + file.getName()
					+ " > /home/adeshgupta/myxhtmlfile.xhtml";*/
			
			
			String cmd = "java -jar /home/adeshgupta/workspace/Assignment2/tika-app-1.13-SNAPSHOT.jar -x "
					+ "/media/adeshgupta/Seagate\\ Expansion\\ Drive/data/application/pdf/"+file.getName()
					+ " > /home/adeshgupta/myxhtmlfile.xhtml";
			
			System.out.println("Command is:"+cmd);
			ProcessBuilder process = new ProcessBuilder("bash", "-c", cmd);
			Process pb = process.start();
			pb.waitFor();

			
			String data = null;
			BufferedReader reader = new BufferedReader(new FileReader("/home/adeshgupta/myxhtmlfile.xhtml"));
			System.out.println("came here");
			String line = null;
			StringBuilder stringBuilder = new StringBuilder();
			String ls = System.getProperty("line.separator");

			try {
				while ((line = reader.readLine()) != null) {
					System.out.println(line + "is");
					stringBuilder.append(line);
					stringBuilder.append(ls);
				}

				data = stringBuilder.toString();
			} finally {
				reader.close();
			}

			// System.out.println("data is:"+data);
			File x = new File("myxhtmlfile.xhtml");
			x.delete();
			data = data.replaceAll("(?s)<!--.*?-->", "");
			data = data.replaceAll("(?s)<script.*?>.*?</script>", "");
			data = data.replaceAll("(?s)<SCRIPT.*?>.*?</SCRIPT>", "");
			data = data.replaceAll("(?s)<style.*?>.*?</style>", "");
			data = data.replaceAll("<[^>]*>", "");

			data = data.replaceAll("[\\n]*", "");
			BufferedWriter br = new BufferedWriter(new FileWriter("output.txt"));
			br.write(data);
			br.close();
			//System.out.println(data);
			String DOI=getDOIforFile(file.getAbsolutePath());
			process = new ProcessBuilder("bash", "-c",
					"curl -G --data-urlencode \"text="+data+"\" http://localhost:8080/processQuantityText > /home/adeshgupta/quant/"+file.getName()+".json");
			pb = process.start();
			pb.waitFor();
			
		}
	}

	public static void generateFiles(String line1, String line2, String line3,File file) throws ParseException {
		try {
			
			
			String n = getDOIforFile(file.getAbsolutePath());
			//System.out.println("");
			n = n.substring(19);
			System.out.println("n:"+n);
			jsonwrite = new FileWriter("/home/adeshgupta/GeoTopicFiles/" + n);
			
			// writer.append(line1+","+line2+","+line3);
			JSONObject obj = new JSONObject();
			System.out.println("*******writing");
			obj.put("Type", "Geotopic_LAT".toString());
			obj.put("Value", line1);
			jsonwrite.write(obj.toJSONString());

			obj = new JSONObject();
			obj.put("Type", "Geotopic_LON".toString());
			obj.put("Value", line2);
			jsonwrite.write(obj.toJSONString());

			obj = new JSONObject();
			obj.put("Type", "Geotopic_NAME".toString());
			obj.put("Value", line3);
			jsonwrite.write(obj.toJSONString());

			jsonwrite.flush();
			jsonwrite.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private static String getDOIforFile(String path) throws ParseException, FileNotFoundException {
		// TODO Auto-generated method stub
		Scanner scn = new Scanner(new File("Mapping_File.json"), "UTF-8");

		while (scn.hasNext()) {

			String nextLine = scn.nextLine();
			if (nextLine.startsWith("[") || nextLine.startsWith("]"))
				nextLine = scn.nextLine();

			JSONObject obj = (JSONObject) new JSONParser().parse(nextLine);
			String name = (String) obj.get("abs_path");
			if (name.equals(path)) {
				String DOI = (String) obj.get("DOI");
				 System.out.println("DOI is: "+DOI);
				return DOI;
			}
		}
		return null;
	}
}