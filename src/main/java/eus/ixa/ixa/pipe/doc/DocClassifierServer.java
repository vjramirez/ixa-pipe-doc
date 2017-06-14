/*
 *  Copyright 2017 Rodrigo Agerri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 */

package eus.ixa.ixa.pipe.doc;

import ixa.kaflib.KAFDocument;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

import org.jdom2.JDOMException;

import com.google.common.io.Files;

public class DocClassifierServer {

  /**
   * Get dynamically the version of ixa-pipe-doc by looking at the MANIFEST
   * file.
   */
  private final String version = CLI.class.getPackage()
      .getImplementationVersion();
  /**
   * Get the git commit of the ixa-pipe-doc compiled by looking at the MANIFEST
   * file.
   */
  private final String commit = CLI.class.getPackage()
      .getSpecificationVersion();
  /**
   * The model.
   */
  private String model = null;
  /**
   * The annotation output format, one of NAF (default) or tabulated.
   */
  private String outputFormat = null;

  /**
   * Construct a DocumentClassification server.
   * 
   * @param properties
   *          the properties
   */
  public DocClassifierServer(Properties properties) {

    Integer port = Integer.parseInt(properties.getProperty("port"));
    model = properties.getProperty("model");
    outputFormat = properties.getProperty("outputFormat");
    ServerSocket socketServer = null;

    try {
      Annotate annotator = new Annotate(properties);
      System.out.println("-> Trying to listen port... " + port);
      socketServer = new ServerSocket(port);

      while (true) {
    	  System.out.println("-> Connected and listening to port " + port);
          try (Socket activeSocket = socketServer.accept();
        		  BufferedReader inFromClient = new BufferedReader(new InputStreamReader(activeSocket.getInputStream(), "UTF-8"));
        		  BufferedWriter outToClient = new BufferedWriter(new OutputStreamWriter(activeSocket.getOutputStream(), "UTF-8"));) {
            System.out.println("-> Received a  connection from: " + activeSocket);
            // get data from client
            String stringFromClient = getClientData(inFromClient);
            // annotate
            String kafToString = getAnnotations(annotator, stringFromClient);
            // send to server
            sendDataToClient(outToClient, kafToString);
          }
      }
      
    } catch (IOException | JDOMException e) {
      e.printStackTrace();
    } finally {
      System.out.println("closing tcp socket...");
      try {
        socketServer.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  
  /**
   * Read data from the client and output to a String.
   * @param inFromClient the client inputstream
   * @return the string from the client
   */
  private String getClientData(BufferedReader inFromClient) {
    StringBuilder stringFromClient = new StringBuilder();
    try {
      String line;
      while ((line = inFromClient.readLine()) != null) {
        if (line.matches("<ENDOFDOCUMENT>")) {
          break;
        }
        stringFromClient.append(line).append("\n");
        if (line.matches("</NAF>")) {
          break;
        }
      }
    }catch (IOException e) {
      e.printStackTrace();
    }
    return stringFromClient.toString();
  }

  /**
   * Send data back to server after annotation.
   * 
   * @param outToClient
   *          the outputstream to the client
   * @param kafToString
   *          the string to be processed
   * @throws IOException
   *           if io error
   */

  private void sendDataToClient(BufferedWriter outToClient, String kafToString) throws IOException {
	    outToClient.write(kafToString);
	    outToClient.close();
	  }
  
  /**
   * OTE annotator.
   * 
   * @param annotator
   *          the annotator
   * @param stringFromClient
   *          the string to be annotated
   * @return the annotation result
   * @throws IOException
   *           if io error
   * @throws JDOMException
   *           if xml error
   */
  private String getAnnotations(Annotate annotator, String stringFromClient)
      throws IOException, JDOMException {
    // get a breader from the string coming from the client
    BufferedReader clientReader = new BufferedReader(
        new StringReader(stringFromClient));
    KAFDocument kaf = KAFDocument.createFromStream(clientReader);
    KAFDocument.LinguisticProcessor newLp = kaf.addLinguisticProcessor(
        "entities", "ixa-pipe-nerc-" + Files.getNameWithoutExtension(model),
        version + "-" + commit);
    newLp.setBeginTimestamp();
    annotator.classify(kaf);
    newLp.setEndTimestamp();
    // get outputFormat
    String kafToString = null;
    if (outputFormat.equalsIgnoreCase("tabulated")) {
      kafToString = annotator.serializeToTabulated(kaf);
    } else {
      kafToString = annotator.serializeToNAF(kaf);
    }
    return kafToString;
  }

}