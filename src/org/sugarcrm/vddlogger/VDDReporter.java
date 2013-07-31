/*
 * Copyright 2011-2013 SugarCRM Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * Please see the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sugarcrm.vddlogger;

import java.io.File;
import java.io.StringReader;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;
import org.xml.sax.InputSource;


/**
 * Entry point for VDD report generation.
 */

public class VDDReporter {

   /**
    * File header for summary.html
    */

   private final String HTML_HEADER_RESOURCE = "summaryreporter-header.txt";

   /**
    * Filename of summary.html
    */

   private final String SUMMARY_FILENAME = "summary.html";

   /**
    * File header for issues.html
    */

   private final String HTML_HEADER_ISSUES_RESOURCE = "issues-header.txt";

   /**
    * Filename of issues.html
    */

   private final String ISSUES_FILENAME = "issues.html";

   /**
    * List of input XML files.
    */

   private ArrayList<File> xmlFiles;

   /**
    * Base directory of test result files.
    */

   private File basedir;

   /**
    * Collection of counts of errors, warnings, and exceptions from
    * all suites.
    */

   private VddLogIssues issues;


   private int count = 0;
   private int passedTests = 0;
   private int failedTests = 0;
   private int blockedTests = 0;
   private int failedAsserts = 0;
   private int passedAsserts = 0;
   private int exceptions = 0;
   private int errors = 0;
   private int watchdog = 0;
   private int hours = 0;
   private int minutes = 0;
   private int seconds = 0;


   /**
    * VDDReporter entry point.
    *
    * @param args  command line arguments
    */

   public static void main(String[] args) {
      ArrayList<File> xml = null;
      File dir = null;
      HashMap<String,File> opts = new HashMap<String,File>();

      for (String arg: args) {
         if (arg.startsWith("--suitefile=")) {
            opts.put("suitefile", new File(arg.replace("--suitefile=", "")));
         } else if (arg.startsWith("--suitedir=")) {
            opts.put("suitedir", new File(arg.replace("--suitedir=", "")));
         } else if (arg.equals("--help")) {
            System.out.println("Usage:\n" +
                               "   VDDReporter --suitefile=<suite.xml>\n" +
                               "   VDDReporter --suitedir=<suite dir>");
            System.exit(0);
         }
      }

      if (opts.containsKey("suitefile")) {
         File f = opts.get("suitefile");

         if (!f.exists()) {
            System.out.println("(!)Suite file '" + f + "' does not exist");
            System.exit(3);
         }

         System.out.println("(*)Processing suite file: '" + f + "'...");
         xml = new ArrayList<File>();
         xml.add(f);
         dir = f.getAbsoluteFile().getParentFile();
      } else if (opts.containsKey("suitedir")) {
         dir = opts.get("suitedir");
         System.out.println("(*)Processing suite directory: '" + dir + "'.");

         File fs[] = dir.listFiles(new java.io.FilenameFilter() {
               public boolean accept(File dir, String name) {
                  boolean ok = name.toLowerCase().endsWith(".xml");
                  if (ok) {
                     System.out.println("(*)Found Suite File: '" + name + "'.");
                  }
                  return ok;
               }
            });

         if (fs == null) {
            System.out.println("(!)Suite directory '" + dir + "' is not valid");
            System.exit(4);
         }

         xml = new ArrayList<File>(java.util.Arrays.asList(fs));

      } else {
         System.out.println("(!)Missing --suitefile or --suitedir!");
         System.exit(2);
      }

      System.out.println("(*)Generating Summary file...");
      VDDReporter r = new VDDReporter(xml, dir);
      r.generateReport();
   }


   /**
    * Instantiate a VDDReporter object
    *
    * @param xmlFiles  list of VDD suite output files
    * @param path      directory containing the XML files
    */

   public VDDReporter(ArrayList<File> xmlFiles, File path) {
      this.xmlFiles = xmlFiles;
      this.basedir = path;
      this.issues = new VddLogIssues();
   }


   /**
    * Convert the VDD output logs into a report.
    */

   public void generateReport() {
      HashMap<String,SuiteData> list = new HashMap<String,SuiteData>();
      java.io.PrintStream report = null;

      File summaryFile = new File(this.basedir, SUMMARY_FILENAME);
      System.out.println("(*)SummaryFile: " + summaryFile);

      try {
         report =
            new java.io.PrintStream(new java.io.FileOutputStream(summaryFile));
      } catch (java.io.FileNotFoundException e) {
         System.err.println("(!)Unable to create summary.html: " + e);
         return;
      }

      report.print(readFile(HTML_HEADER_RESOURCE));

      for (File xml: xmlFiles) {
         SuiteData suiteData = null;
         try {
            suiteData = parseXMLFile(xml);
         } catch (Exception e) {
            e.printStackTrace();
            System.out.println("(!)Failed to parse " + xml + ": " + e);
            continue;
         }
         list.put(suiteData.suitename, suiteData);
      }

      String keys[] = list.keySet().toArray(new String[0]);
      java.util.Arrays.sort(keys);

      for (String key: keys) {
         report.print(processSuite(key, list.get(key)));
      }

      report.print(generateHTMLFooter());
      report.print("\n</body>\n</html>\n");
      report.close();

      this.writeIssues();
   }


   /**
    * Read an entire file into a String
    *
    * <p>The file will be opened from either the directory tree or
    * VDDReporter's jar file.</p>
    *
    * @param name  name of the file to open
    * @return the contents of the file
    */

   private String readFile(String name) {
      Class c = VDDReporter.class;
      String nm = c.getName().replace('.', '/');
      String jar = c.getResource("/" + nm + ".class").toString();
      java.io.InputStream is = null;

      if (jar.startsWith("jar:")) {
         is = c.getResourceAsStream(name);
      } else {
         try {
            is = new java.io.FileInputStream(new File(c.getResource(name).getFile()));
         } catch (java.io.FileNotFoundException e) {
            System.err.println("(!)" + name + " not found: " + e);
            return "";
         }
      }

      java.io.BufferedReader b =
         new java.io.BufferedReader(new java.io.InputStreamReader(is));

      String out = "";
      String line;
      try {
         while ((line = b.readLine()) != null) {
            out += line + "\n";
         }
      } catch (java.io.IOException e) {
         System.err.println("(!)Error reading " + name + ": " + e);
      }

      try { b.close(); } catch (java.io.IOException e) {}

      return out;
   }


   private void writeIssues() {
      String[] errors_keys = null;
      String[] warnings_keys = null;
      String[] except_keys = null;
      String line = "";
      HashMap<String, Integer> tmpMap = null;

      File issuesFile = new File(this.basedir, ISSUES_FILENAME);

      System.out.println("(*)Writing issues file: " + issuesFile);

      errors_keys = sortIssue(this.issues.getData().get("errors"));
      warnings_keys = sortIssue(this.issues.getData().get("warnings"));
      except_keys = sortIssue(this.issues.getData().get("exceptions"));

      try {
         java.io.BufferedWriter out =
            new java.io.BufferedWriter(new java.io.FileWriter(issuesFile));
         out.write(readFile(HTML_HEADER_ISSUES_RESOURCE));

         tmpMap = this.issues.getData().get("errors");
         out.write("<table>\n");
         out.write("<tr>\n<td class=\"td_header_master\" colspan=\"2\">Errors:</td>\n</tr>\n");
         out.write("<tr>\n\t<td class=\"td_header_count\">Count:</td>\n\t<td class=\"td_header_sub\">Issue:</td>\n</tr>\n");
         for (int i = errors_keys.length -1; i >= 0 ; i--) {
            int count = tmpMap.get(errors_keys[i]);
            errors_keys[i] = errors_keys[i].replaceAll("<", "&lt");
            errors_keys[i] = errors_keys[i].replaceAll(">", "&gt");
            out.write("<tr class=\"unhighlight\" onmouseout=\"this.className='unhighlight'\" onmouseover=\"this.className='highlight'\">\n");
            String n = String.format("\t<td class=\"td_count_data\">%d</td>\n\t<td class=\"td_file_data\">%s</td>\n", count, errors_keys[i]);
            out.write(n);
            out.write("</tr>\n");
         }
         out.write("</table>\n");
         out.write("\n<hr></hr>\n");

         tmpMap = this.issues.getData().get("exceptions");
         out.write("<table>\n");
         out.write("<tr>\n<td class=\"td_header_master\" colspan=\"2\">Exceptions:</td>\n</tr>\n");
         out.write("<tr>\n\t<td class=\"td_header_count\">Count:</td>\n\t<td class=\"td_header_sub\">Issue:</td>\n</tr>\n");
         for (int i = except_keys.length -1; i >= 0 ; i--) {
            int count = tmpMap.get(except_keys[i]);
            out.write("<tr class=\"unhighlight\" onmouseout=\"this.className='unhighlight'\" onmouseover=\"this.className='highlight'\">\n");
            except_keys[i] = except_keys[i].replaceAll("<", "&lt");
            except_keys[i] = except_keys[i].replaceAll(">", "&gt");
            String n = String.format("\t<td class=\"td_count_data\">%d</td>\n\t<td class=\"td_file_data\">%s</td>\n", count, except_keys[i]);
            out.write(n);
            out.write("</tr>\n");
         }
         out.write("</table>\n");
         out.write("\n<hr></hr>\n");

         tmpMap = this.issues.getData().get("warnings");
         out.write("<table>\n");
         out.write("<tr>\n\t<td class=\"td_header_master\" colspan=\"2\">Warnings:</td>\n</tr>\n");
         out.write("<tr>\n\t<td class=\"td_header_count\">Count:</td>\n\t<td class=\"td_header_sub\">Issue:</td>\n</tr>\n");
         for (int i = warnings_keys.length -1; i >= 0 ; i--) {
            int count = tmpMap.get(warnings_keys[i]);
            out.write("<tr class=\"unhighlight\" onmouseout=\"this.className='unhighlight'\" onmouseover=\"this.className='highlight'\">\n");
            warnings_keys[i] = warnings_keys[i].replaceAll("<", "&lt");
            warnings_keys[i] = warnings_keys[i].replaceAll(">", "&gt");
            String n = String.format("\t<td class=\"td_count_data\">%d</td>\n\t<td class=\"td_file_data\">%s</td>\n", count, warnings_keys[i]);
            out.write(n);
            out.write("</tr>\n");
         }
         out.write("</table>\n");

         out.write("</body></html>\n");
         out.close();
      } catch (Exception exp ) {
         exp.printStackTrace();
      }

      System.out.printf("(*)Finished writing issues file.\n");

   }

   private String[] sortIssue(HashMap<String, Integer> map) {
      String[] keys = null;

      keys = map.keySet().toArray(new String[0]);

      for (int i = 0; i <= keys.length -1; i++) {
         int count = map.get(keys[i]);
         keys[i] = String.format("%d:%s", count, keys[i]);
      }

      java.util.Arrays.sort(keys);
      for (int i = 0; i <= keys.length -1; i++) {
         keys[i] = keys[i].replaceFirst("\\d+:", "");
      }

      return keys;
   }


   private boolean isRestart(Node node) {
      boolean result = false;
      NodeList parent = node.getParentNode().getChildNodes();

      for (int i = 0; i <= parent.getLength() -1; i++) {
         Node tmp = parent.item(i);
         String name = tmp.getNodeName();
         if (name.contains("isrestart")) {
            result = Boolean.valueOf(tmp.getTextContent());
            break;
         }
      }

      return result;
   }

   private boolean isLibTest(Node node) {
      NodeList parent = node.getParentNode().getChildNodes();

      for (int i = 0; i <= parent.getLength() -1; i++) {
         Node tmp = parent.item(i);
         String name = tmp.getNodeName();
         if (name.contains("testfile")) {
            File fd = new File(tmp.getTextContent());
            String path = fd.getParent();
            if (path == null) {
               /*
                * Filename contains no path information, so it's
                * impossible to know whether this is in the lib.
                */
               return false;
            }
            path = path.toLowerCase();

            if (path.contains("lib")) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean isBlocked(Node node) {
      boolean result = false;
      NodeList parent = node.getParentNode().getChildNodes();

      for (int i = 0; i <= parent.getLength() -1; i++) {
         Node tmp = parent.item(i);
         String name = tmp.getNodeName();
         if (name.contains("blocked")) {
            int blocked = Integer.valueOf(tmp.getTextContent());
            if (blocked != 0) {
               result = true;
            } else {
               result = false;
            }
            break;
         }
      }

      return result;
   }


   /**
    * Summary data from the suite log.
    */

   private class SuiteData {
      public int passed = 0;
      public int failed = 0;
      public int blocked = 0;
      public int asserts = 0;
      public int failedAsserts = 0;
      public int errors = 0;
      public int exceptions = 0;
      public int watchdog = 0;
      public String runtime = "";
      public boolean truncated = false;
      public String suitename;
      public ArrayList<HashMap<String,String>> testlogs = null;
   }


   /**
    * Read the summary data from the suite log.
    *
    * @param doc  XML document representing the suite log
    * @return SuiteData object with the data
    */

   private SuiteData getSuiteData(Document doc) {
      SuiteData d = new SuiteData();
      
      d.suitename = getSuiteName(doc);
      d.passed = getAmtPassed(doc);
      d.blocked = getAmtBlocked(doc);
      d.failed = getAmtFailed(doc);
      d.watchdog = getAmtwatchdog(doc);
      d.asserts = getAmtAsserts(doc);
      d.failedAsserts = getAmtAssertsF(doc);
      d.exceptions = getAmtExceptions(doc);
      d.errors = getAmtErrors(doc);
      d.runtime = getRunTime(doc);
      d.truncated = getTruncated(doc);
      d.testlogs = this.getTestLogs(doc);

      return d;
   }


   /**
    * Generate a row in the suite summary report
    *
    * @param suiteName  name of the test suite being processed
    * @param data       data for this test suite
    * @return a single row for output to summary.html
    */

   private String processSuite(String suiteName, SuiteData d) {
      int total  = d.failedAsserts + d.exceptions + d.errors;
      String hl  = "highlight";
      String uhl = "unhighlight";
      String html;
      String cls;

      if (d.truncated || d.passed + d.failed + d.blocked == 0) {
         hl += "_truncated";
         uhl += "_truncated";
      }

      /* Row prologue. */
      html = ("<tr id=\"" + count + "\" class=\"" + uhl + "\"" +
                     "    onmouseover=\"this.className='" + hl + "'\"" +
              "    onmouseout=\"this.className='" + uhl + "'\">\n" +
              "   <td class=\"td_file_data\">\n" +
              "      <a href=\"" + d.suitename+"/"+d.suitename + ".html\">" +
              d.suitename + ".xml</a>\n" +
              "   </td>\n");

      /* Tests column (passed/failed/blocked). */
      cls = (d.blocked != 0) ? "td_run_data_error" : "td_run_data";
      html += ("   <td class=\"" + cls + "\">" +
               (d.passed + d.failed) + "/" + (d.passed + d.failed + d.blocked) +
               "</td>\n" +
               "   <td class=\"td_passed_data\">" + d.passed + "</td>\n" + 
               "   <td class=\"td_failed_data\">" + d.failed + "</td>\n" +
               "   <td class=\"td_blocked_data\">" + d.blocked + "</td>\n");

      /* Results column */

      /* Watchdog timer expiries */
      cls = (d.watchdog != 0) ? "td_watchdog_error_data" : "td_watchdog_data";
      html += "   <td class=\"" + cls + "\">" + d.watchdog + "</td>\n";

      /* Passed asserts */
      html += "   <td class=\"td_assert_data\">" + d.asserts + "</td>\n";

      /* Failed asserts */
      cls = (d.failedAsserts != 0) ? "td_assert_error_data" : "td_assert_data";
      html += "   <td class=\"" + cls + "\">" + d.failedAsserts + "</td>\n";

      /* Exceptions */
      cls = (d.exceptions != 0) ? "td_exceptions_error_data" :
                                  "td_exceptions_data";
      html += "   <td class=\"" + cls + "\">" + d.exceptions + "</td>\n";

      /* Errors */
      cls = (d.errors != 0) ? "td_exceptions_error_data" : "td_exceptions_data";
      html += "   <td class=\"" + cls + "\">" + d.errors + "</td>\n";

      /* Total Failures */
      cls = (total != 0) ? "td_total_error_data" : "td_total_data";
      html += "   <td class=\"" + cls + "\">" + total + "</td>\n";

      /* Runtime */
      html += "   <td class=\"td_time_data\">" + d.runtime + "</td>\n";

      /* Row epilogue */
      html += "</tr>\n";

      /*
       * Generate the suite report.
       */
      VddSuiteReporter r = new VddSuiteReporter(d.suitename,
                                                this.basedir.toString(),
                                                d.testlogs);
      r.generateReport();
      this.issues.appendIssues(r.getIssues());

      return html;
   }


   /**
    * Create the HTML table footer for the summary report
    *
    * @return the table footer
    */

   private String generateHTMLFooter() {
      int n1 = 0;
      int n2 = 0;
      String footerrun = "td_footer_run";
      String failedtd = "td_footer_failed";
      String blockedtd = "td_footer_blocked";

      n1 = passedTests + failedTests;
      n2 = passedTests + failedTests + blockedTests;
      if (n1 != n2) {
         footerrun = "td_footer_run_err";
      }

      if (failedTests == 0) {
         failedtd = "td_footer_failed_zero";
      }

      if (blockedTests == 0) {
         blockedtd = "td_footer_blocked_zero";
      }

      String footer = "<tr id=\"totals\"> \n" +
            "\t <td class=\"td_header_master\">Totals:</td>" +
            String.format("\t <td class=\"%s\">"+(passedTests + failedTests)+"/"+(passedTests + failedTests + blockedTests)+"</td>", footerrun) +
            "\t <td class=\"td_footer_passed\">"+passedTests+"</td>" +
            String.format("\t <td class=\"%s\">"+failedTests+"</td>", failedtd) +
            String.format("\t <td class=\"%s\">"+blockedTests+"</td>", blockedtd) +
            "\t <td class=\"td_footer_watchdog\">"+watchdog+"</td>" +
            "\t <td class=\"td_footer_passed\">"+passedAsserts+"</td>" +
            "\t <td class=\"td_footer_assert\">"+failedAsserts+"</td>" +
            "\t <td class=\"td_footer_exceptions\">"+exceptions+"</td>" +
            "\t <td class=\"td_footer_watchdog\">"+errors+"</td>" +
            "\t <td class=\"td_footer_total\">"+(failedAsserts + exceptions + errors)+"</td>" +
            "\t <td class=\"td_footer_times\">"+printTotalTime(hours, minutes, seconds)+"</td>" +
            "</tr>" +
            "</tbody>" +
            "</table>";

      return footer;
   }

   /**
    * Deal with log files that are missing end tags.
    *
    * <p>This is called when xml processing has failed, and that
    * only happens when the datafile ends prematurely.  From this
    * point of view, there are three scenarios:</p>
    *
    * <ol>
    *   <li>File is empty</li>
    *   <li>File is correctly formed xml, but missing end tags</li>
    *   <li>File ends mid-tag</li>
    * </ol>
    *
    * <p>For the first case, we create an almost empty file with
    * the bare minimum of tags and use the file name for the
    * suite file name.  This should be noticable by the user as a
    * problem.</p>
    *
    * <p>For the second and third, we find the last valid entry
    * and cut the file off there, then add terminating tags.</p>
    *
    * <p>For all cases, a &lt;truncated/&gt; tag is added.  This
    * tells the report generation engine to highlight this file's
    * entry so the user will be incited to look into the problem
    * (e.g. did the test machine crash midway through the
    * test?)</p>
    *
    * @param xml a log file that has already failed parsing
    * @return an InputSource with added end tags suitable for re-parsing
    */

   private InputSource endTagHack(File xml) throws Exception {
      if (xml.length() >= (2L << 31)) {
         /*
          * The CharBuffer below uses an int for its
          * buffer size.  This limits us to files less
          * than 2GB.  That's probably the least of the
          * problems if this code is getting called,
          * but make sure here it's flagged.  Should
          * the user notice and file a bug, this code
          * can be revisited.
          */
         System.out.println("(!)Warning: File > 2GB (" + xml.length() + "). Further truncation will occur.");
      }
      CharBuffer cbuf = CharBuffer.allocate((int)xml.length());
      java.io.FileReader f = new java.io.FileReader(xml);
      f.read(cbuf);
      cbuf.rewind();

      /* First case */
      if (cbuf.length() == 0) {
         String contents = "<data><truncated/><suite><suitefile>" + xml.getName() + "</suitefile></suite></data>\n";
         return new InputSource(new StringReader(contents));
      }

      /* Second and third cases. */
      String mungedXml = cbuf.toString();

      /* Scan backward through the file, looking for an appropriate end tag. */
      String endTags[] = {"</test>", "</suite>", "</data>"}; // Yes, the order is significant.
      for (int k = cbuf.length() - 1; k >= 0; k--) {
         if (cbuf.charAt(k) == '<') {
            Boolean foundEndTag = false;
            for (int t = 0; t < endTags.length; t++) {
               if (mungedXml.regionMatches(k, endTags[t], 0, endTags[t].length())) {
                  foundEndTag = true;
                  mungedXml = mungedXml.substring(0, k);
               }
               if (foundEndTag) {
                  if (t == 2) {
                     mungedXml += "<truncated/>";
                  }
                  mungedXml += endTags[t];
               }
            }
            if (foundEndTag) {
               break;
            }
         }
      }

      return new InputSource(new StringReader(mungedXml));
   }

   private SuiteData parseXMLFile(File xml) throws Exception {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbf.newDocumentBuilder();
      db.setErrorHandler(new VddErrorHandler());
      Document dom = null;

      try {
         dom = db.parse(xml);
      } catch(SAXParseException e) {
         System.out.println("(!)Error parsing log file (" + e.getMessage() + ").  Retrying with end tag hack...");
         InputSource is = endTagHack(xml);
         dom = db.parse(is);
         System.out.println("(*)Success!");
      }

      return getSuiteData(dom);
   }

   /**
    * get the number of tests that passed within this suite document
    * @ param d - the Document containing suite run data
    * @ return the number of tests that passed
    *
    */
   private int getAmtPassed(Document d) {
      int n = 0;
      Element el;
      NodeList nl = d.getElementsByTagName("result");
      boolean isrestart = false;
      boolean islibfile = false;

      for (int i = 0; i < nl.getLength(); i ++) {
         el = (Element)nl.item(i);
         if (el.getFirstChild().getNodeValue().compareToIgnoreCase("Passed") == 0) {
            islibfile = isLibTest(nl.item(i));
            isrestart = isRestart(nl.item(i));

            if (isrestart) {
               continue;
            }

            if (islibfile) {
               continue;
            }

            n ++;
         }
      }

      //global passedTests variable
      passedTests += n;
      return n;
   }

   /**
    * get the number of tests that failed within this suite document
    * @ param d - the Document containing suite run data
    * @ return the number of tests that failed
    *
    */
   private int getAmtFailed(Document d) {
      int n = 0;
      Element el;
      boolean isrestart = false;
      boolean isblocked = false;
      NodeList nl = d.getElementsByTagName("result");
      boolean islibfile = false;

      for (int i = 0; i < nl.getLength(); i ++){
         el = (Element)nl.item(i);
         if (el.getFirstChild().getNodeValue().compareToIgnoreCase("Failed") == 0) {
            isrestart = isRestart(nl.item(i));
            isblocked = isBlocked(nl.item(i));
            islibfile = isLibTest(nl.item(i));
            if (isrestart) {
               continue;
            }

            if (isblocked) {
               continue;
            }

            if (islibfile) {
               continue;
            }

            n ++;
         }
      }

      //global failedTests variable
      failedTests += n;
      return n;
   }

   /**
    * get the number of tests that was blocked within this suite document
    * @ param d - the Document containing suite run data
    * @ return the number of tests that was blocked
    *
    */
   private int getAmtBlocked(Document d) {
      int n = 0;
      Element el;
      boolean isrestart = false;
      boolean islibfile = false;
      NodeList nl = d.getElementsByTagName("blocked");

      for (int i = 0; i < nl.getLength(); i ++) {
         el = (Element)nl.item(i);
         if (el.getFirstChild().getNodeValue().compareToIgnoreCase("1") == 0) {
            isrestart = isRestart(nl.item(i));
            islibfile = isLibTest(nl.item(i));
            if (isrestart) {
               continue;
            }

            if (islibfile) {
               continue;
            }

            n ++;
         }
      }

      //global blockedTests variable
      blockedTests += n;
      return n;
   }

   private ArrayList<HashMap<String, String>> getTestLogs(Document d) {
      ArrayList<HashMap<String, String>> result = new ArrayList<HashMap<String,String>>();
      NodeList nodes = d.getElementsByTagName("test");

      for (int i = 0; i <= nodes.getLength() -1; i++) {
         Node currNode = nodes.item(i);
         HashMap<String, String> newHash = new HashMap<String, String>();
         NodeList kids = currNode.getChildNodes();

         for (int x = 0; x <= kids.getLength() -1; x++) {
            Node kidNode = kids.item(x);
            if (kidNode.getNodeName().contains("testlog")) {
               newHash.put(kidNode.getNodeName(), kidNode.getTextContent());
            } else if (kidNode.getNodeName().contains("isrestart")) {
               newHash.put(kidNode.getNodeName(), kidNode.getTextContent().toLowerCase());
            }
         }
         result.add(newHash);
      }

      return result;
   }

   /**
    * get the number of assertions that passed within this suite document
    * @ param d - the Document containing suite run data
    * @ return the number of passed assertions
    *
    */
   private int getAmtAsserts(Document d) {
      int n = 0;
      Element el;
      boolean isrestart = false;
      NodeList nl = d.getElementsByTagName("passedasserts");

      for (int i = 0; i < nl.getLength(); i ++) {
         el = (Element)nl.item(i);
         if (Integer.parseInt(el.getFirstChild().getNodeValue()) > 0){
            isrestart = isRestart(nl.item(i));
            if (isrestart) {
               continue;
            }
            n += Integer.parseInt(el.getFirstChild().getNodeValue());
         }
      }
      //global passedAsserts
      passedAsserts += n;
      return n;
   }

   /**
    * get the number of assertions that failed within this suite document
    * @ param d - the Document containing suite run data
    * @ return the number of failed assertions
    *
    */
   private int getAmtAssertsF(Document d) {
      int n = 0;
      Element el;
      boolean isrestart = false;
      NodeList nl = d.getElementsByTagName("failedasserts");

      for (int i = 0; i < nl.getLength(); i ++) {
         el = (Element)nl.item(i);
         if (Integer.parseInt(el.getFirstChild().getNodeValue()) > 0) {
            isrestart = isRestart(nl.item(i));
            if (isrestart) {
               continue;
            }
            n += Integer.parseInt(el.getFirstChild().getNodeValue());
         }
      }
      //global failedAsserts
      failedAsserts += n;
      return n;
   }

   /**
    * get the number of watchdogs within this suite document
    * @ param d - the Document containing suite run data
    * @ return the number of watchdogs
    *
    */
   private int getAmtwatchdog(Document d) {
      int n = 0;
      Element el;
      boolean isrestart = false;
      NodeList nl = d.getElementsByTagName("watchdog");

      for (int i = 0; i < nl.getLength(); i ++) {
         el = (Element)nl.item(i);
         if (Integer.parseInt(el.getFirstChild().getNodeValue()) > 0) {
            isrestart = isRestart(nl.item(i));
            if (isrestart) {
               continue;
            }
            n += Integer.parseInt(el.getFirstChild().getNodeValue());
         }
      }

      watchdog += n;
      return n;
   }

   /**
    * get the number of exceptions within this suite document
    * @ param d - the Document containing suite run data
    * @ return the number of exceptions
    *
    */
   private int getAmtExceptions(Document d) {
      int n = 0;
      Element el;
      boolean isrestart = false;
      NodeList nl = d.getElementsByTagName("exceptions");

      for (int i = 0; i < nl.getLength(); i ++) {
         el = (Element)nl.item(i);
         if (Integer.parseInt(el.getFirstChild().getNodeValue()) > 0) {
            isrestart = isRestart(nl.item(i));
            if (isrestart) {
              continue;
            }
            n += Integer.parseInt(el.getFirstChild().getNodeValue());
         }
      }
      //global exceptions
      exceptions += n;
      return n;
   }

   private int getAmtErrors(Document d) {
      int n = 0;
      Element el;
      boolean isrestart = false;
      NodeList nl = d.getElementsByTagName("errors");
      for (int i = 0; i < nl.getLength(); i ++) {
         el = (Element)nl.item(i);
         if (Integer.parseInt(el.getFirstChild().getNodeValue()) > 0) {
            isrestart = isRestart(nl.item(i));
            if (isrestart) {
               continue;
            }
            n += Integer.parseInt(el.getFirstChild().getNodeValue());
         }
      }
      //global errors
      errors += n;
      return n;
   }

   /**
    * calculates the running time fromt a suite xml file, and return it in a html-friendly format
    * @param d - document to get time data from
    * @return - total run time for this suite test in String
    */
   private String getRunTime(Document d) {
      String  temp;
      int h = 0, m = 0, s = 0;
      Element el;
      NodeList nl = d.getElementsByTagName("totaltesttime");

      for (int i = 0; i < nl.getLength(); i ++) {
         el = (Element)nl.item(i);
         temp = el.getFirstChild().getNodeValue();
         h += Integer.parseInt(temp.substring(0, temp.indexOf(":")));
         m += Integer.parseInt(temp.substring(2, temp.lastIndexOf(":")));
         s += Integer.parseInt(temp.substring(temp.lastIndexOf(":")+1, temp.indexOf(".")));
      }

      this.hours += h;
      this.minutes += m;
      this.seconds += s;
      return printTotalTime(h, m , s);
   }

   /**
    * formats and returns a correct String representation from inputs of hours, minutes and seconds
    * @param hours
    * @param minutes
    * @param seconds
    * @return correctly formatted time in String
    */
   private String printTotalTime(int h, int m, int s) {
      String time = "";

      //carry over seconds
      while (s >= 60) {
         m ++;
         s -= 60;
      }
      //carry over minutes
      while(m >= 60) {
         h ++;
         m -= 60;
      }

      String ms = ""+ m, ss = ""+ s;
      if (m < 10) {
         ms = "0"+m;
      }

      if (s < 10) {
         ss = "0"+s;
      }
      time = "0"+h+":"+ms+":"+ss;
      return time;
   }

   /**
    * get the name of the suite, without extension
    * @param d
    * @return suite name
    */
   private String getSuiteName(Document d) {
      String name = "";
      NodeList nl = d.getElementsByTagName("suitefile");

      if (nl != null && nl.getLength() > 0) {
         Element el = (Element)nl.item(0);
         name = el.getFirstChild().getNodeValue();
      }

      name = name.substring(0, name.indexOf("."));
      return name;
   }

   /**
    * Get whether the log file was truncated
    */
   private boolean getTruncated(Document d) {
      NodeList nl = d.getElementsByTagName("truncated");
      return nl.getLength() > 0;
   }
}
