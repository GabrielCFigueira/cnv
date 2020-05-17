package pt.ulisboa.tecnico.cnv.loadbalancer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.util.Map;
import java.util.HashMap;

import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;

public class LoadBalancer {

	private static Map<Instance, Boolean> _instances = new HashMap<Instance, Boolean>();

	public static void main(final String[] args) throws Exception {

		final HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

		server.createContext("/sudoku", new RedirectHandler());

		// be aware! infinite pool of threads!
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();

		final AutoScaler as = new AutoScaler(_instances);

		System.out.println(server.getAddress().toString());


			
		Thread thread = new Thread(){	
			public void run(){
				try{		
					while(true) {	
						Thread.sleep(1000);
						as.run();
					}				
				} catch(InterruptedException e){	
					e.printStackTrace();
					System.exit(-1);
				}
			}
		};
		thread.start();
	}

	public static String parseRequestBody(InputStream is) throws IOException {
        InputStreamReader isr =  new InputStreamReader(is,"utf-8");
        BufferedReader br = new BufferedReader(isr);

        // From now on, the right way of moving from bytes to utf-8 characters:

        int b;
        StringBuilder buf = new StringBuilder(512);
        while ((b = br.read()) != -1) {
            buf.append((char) b);

        }

        br.close();
        isr.close();

        return buf.toString();
    }

    static class RedirectHandler implements HttpHandler {
      
        @Override
        public void handle(HttpExchange t) throws IOException {
		try {	
			URL url = null;
			synchronized(_instances) {
				for(Instance instance : _instances.keySet()) {
					url = new URL("http://" + instance.getPublicDnsName() + ":8000/sudoku?" + t.getRequestURI().getQuery());
					break;
				}
			}
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			//In order to request a server
			con.setDoOutput(true);
			con.setRequestMethod("POST");
			con.setRequestProperty("Accept", "*/*");
			con.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");

			OutputStream outStream = con.getOutputStream();
			OutputStreamWriter outStreamWriter = new OutputStreamWriter(outStream, "UTF-8");
			String s = parseRequestBody(t.getRequestBody());
			System.out.println("Board: "+ s);
			outStreamWriter.write(s);
			outStreamWriter.flush();
			outStreamWriter.close();
			outStream.close();

			//In order to obtain the response of the server
			BufferedReader in = new BufferedReader(
			new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer content = new StringBuffer();
			while ((inputLine = in.readLine()) != null) {
				content.append(inputLine);
			}
			in.close();
			System.out.println(content.toString());

			//In order to return to the client
			final Headers hdrs = t.getResponseHeaders();

			hdrs.add("Content-Type", "application/json");

			hdrs.add("Access-Control-Allow-Origin", "*");

			hdrs.add("Access-Control-Allow-Credentials", "true");
			hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
			hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

			t.sendResponseHeaders(200, content.toString().length());


			final OutputStream os = t.getResponseBody();
			OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
			osw.write(content.toString());
			osw.flush();
			osw.close();

			os.close();
			

			con.disconnect();

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e.getMessage());
		}
        }
      }

	

	
}
