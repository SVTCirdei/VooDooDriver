/*
 * Copyright 2011-2012 SugarCRM Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License.  You
 * may may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  Please see the License for the specific language
 * governing permissions and limitations under the License.
 */

package org.sugarcrm.voodoodriver;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.interactions.Mouse;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;


/**
 * Base class for VooDooDriver browser support.
 *
 * @author trampus
 * @author Jon duSaint
 */

public abstract class Browser {

   /**
    * {@link WebDriver} backend
    */

   private WebDriver Driver = null;

   /**
    * Whether the browser window is closed.
    */

   private boolean closed = true;

   /**
    * The browser profile.
    */

   protected String profile = null;

   /**
    * {@link Reporter} object used for logging.
    */

   private Reporter reporter = null;

   /**
    * Page assert file.
    */

   private File assertPageFile = null;

   /**
    * {@link PageAsserter} object.
    */

   private PageAsserter asserter = null;

   /**
    * Size of the browser window when not maximized.
    */

   private Dimension browserSize;

   /**
    * True if new browser windows should be maximized immediately after open.
    */

   private boolean maximizeWindows = false;

   /**
    * Directory into which to save the web driver logs.
    */

   protected File webDriverLogDirectory = null;

   /**
    * Number of seconds to use for Selenium's page load timeout.
    */

   protected int seleniumTimeout;


   /**
    * Set the name of the browser profile.
    *
    * @param profile  browser profile name
    */

   public void setProfile(String profile) {
      this.profile = profile;
   }


   /**
    * Get the name of the current browser profile.
    *
    * @return current browser profile name
    */
   public String getProfile() {
      return this.profile;
   }


   /**
    * Set the value for Selenium's page load timeout.
    *
    * @param timeout  page load timeout in seconds
    */

   public void setSeleniumTimeout(int timeout) {
      this.seleniumTimeout = timeout;
   }


   /**
    * Get the {@link Mouse} object for access to the raw input device.
    *
    * @return the {@link Mouse} device for this machine
    */

   public abstract Mouse getMouse();


   /**
    * Create a new browser window.
    */

   public abstract void newBrowser();


   /**
    * Set this browser's download directory.
    *
    * @param dir  the download directory
    */

   public abstract void setDownloadDirectory(String dir);


   /**
    * Enable WebDriver logging.
    *
    * <p>This currently only works on firefox and chrome.</p>
    *
    * @param directory  the WebDriver log directory
    */

   public void enableWebDriverLogging(File directory) {
      this.webDriverLogDirectory = directory;
   }


   /**
    * Create a log file name in the specified directory with the
    * specified base name.
    *
    * @param dir   log directory
    * @param base  base name for log file
    * @return complete path to log file 
    */

   protected File makeLogfileName(File dir, String base) {
      base += "-" + String.format("%1$tm-%1$td-%1$tY-%1$tH-%1$tM-%1$tS.%1$tL",
                                  new Date()) + ".log";
      return new File(dir, base);
   }


   /**
    * Log the capability kvps listed by RemoteWebDriver.
    *
    * <p>This dataset includes such interesting information as the
    * browser type and version and the OS.</p>
    */

   protected void logDriverCaps() {
      System.out.println("(*)WebDriver Capabilities:");
      java.util.Map<String,?> m =
         ((RemoteWebDriver)this.Driver).getCapabilities().asMap();

      String[] keys = m.keySet().toArray(new String[0]);
      int col = 0;
      java.util.Arrays.sort(keys);
      for (String key: keys) {
         if (key.length() > col) {
            col = key.length();
         }
      }

      for (String key: keys) {
         System.out.printf("--)%" + String.valueOf(col + 2) + "s: %s\n",
                           key, m.get(key));
      }
   }


   /**
    * Open the specified URL in the browser.
    *
    * @param url  URL to open
    */

   public void url(String url) {
      this.Driver.navigate().to(url);
   }


   /**
    * Refresh/reload the current browser location.
    */

   public void refresh() {
      this.Driver.navigate().refresh();
   }


   /**
    * Navigate forward one page in the browser.
    */

   public void forward() {
      this.Driver.navigate().forward();
   }


   /**
    * Navigate back one page in the browser.
    */

   public void back() {
      this.Driver.navigate().back();
   }


   /**
    * Maximize the browser window.
    */

   public void maximize() {
      if (this.browserSize == null) {
         this.browserSize = this.Driver.manage().window().getSize();
         this.Driver.manage().window().maximize();
      }
   }


   /**
    * Restore the browser window.
    */

   public void restore() {
      if (this.browserSize != null) {
         this.Driver.manage().window().setSize(this.browserSize);
         this.browserSize = null;
      }
   }


   /**
    * Close the browser window.
    */

   public void close() {
      this.Driver.close();
      this.setBrowserClosed();
   }


   /**
    * Set whether to maximize new windows.
    *
    * @param maximize  true if new windows should be maximized
    */

   public void maximizeBrowserWindows(boolean maximize) {
      this.maximizeWindows = maximize;
   }


   /**
    * Force the browser window to close via the native operating system.
    */

   public abstract void forceClose();


   /**
    * Return whether the browser window is closed.
    *
    * @return true if the browser window is close, false otherwise
    */

   public boolean isClosed() {
      return this.closed;
   }


   /**
    * Set the browser closed state to true.
    */

   public void setBrowserClosed() {
      this.closed = true;
   }

   /**
    * Set the browser closed state to false.
    */

   public void setBrowserOpened() {
      if (this.maximizeWindows == true) {
         maximize();
      }
      this.closed = false;
   }


   /**
    * Fetch the page source for the loaded page.
    *
    * @return HTML source for the current page
    */

   public String getPageSource() {
      try {
         return this.Driver.getPageSource();
      } catch (org.openqa.selenium.UnhandledAlertException e) {
         this.reporter.warning("Unhandled alert when getting page source",
                               false);
      } catch (org.openqa.selenium.WebDriverException e) {
         this.reporter.warning("WebDriverException when getting page source" + e,
                               false);
      }

      return "";
   }


   /**
    * Execute javascript in the browser.
    *
    * This method executes a javascript string in the context of the
    * browser.  During execution, a variable, "CONTROL", is created
    * for use by the script.
    *
    * @param script  The javascript to run in the browser.
    * @param element The Element to use on the page as the CONTROL var.
    * @return the {@link Object} returned by the javascript code or
    *         null if the script throws an exception
    */

   public Object executeJS(String script, WebElement element) {
      JavascriptExecutor js = (JavascriptExecutor)this.Driver;

      try {
         if (element != null) {
            return js.executeScript(script, element);
         } else {
            return js.executeScript(script);
         }
      } catch (org.openqa.selenium.WebDriverException e) {
         this.reporter.Warn("Exception during javascript execution: " + e);
      }

      return "undefined";
   }


   /**
    * Fire a javascript event in the browser for an HTML element.
    *
    * @param element    the HTML element
    * @param eventType  which javascript event to fire
    * @return the {@link String} value returned by the event
    */

   public String fire_event(WebElement element, String eventType) {
      Object result;
      String eventjs_src = "";
      JavascriptEventTypes type = null;
      String tmp_type = eventType.toUpperCase().replaceAll("ON", "");

      try {
         UIEvents.valueOf(tmp_type);
         type = JavascriptEventTypes.UIEvent;
      } catch (Exception eo) {
         try {
            HTMLEvents.valueOf(tmp_type);
            type = JavascriptEventTypes.HTMLEvent;
         } catch (Exception ei) {
            return "";
         }
      }

      switch (type) {
      case HTMLEvent:
         break;
      case UIEvent:
         eventjs_src = this.generateUIEvent(UIEvents.valueOf(tmp_type));
         break;
      }

      result = this.executeJS(eventjs_src, element);

      return (result == null) ? "" : result.toString();
   }


   /**
    * Generate a browser event of the specified type.
    *
    * @param type  the type of browser event
    * @return the resulting browser event code
    */

   public String generateUIEvent(UIEvents type) {
      if (type == UIEvents.FOCUS) {
         return ("arguments[0].focus();\n" +
                 "return 0;\n");
      }

      String e = type.toString().toLowerCase();

      return ("var ele = arguments[0];\n" +
              "if (document.createEvent) {\n" +
              "   var eo = document.createEvent('MouseEvents');\n" +
              "   eo.initMouseEvent('" + e + "',\n" +
              "                     true, true, window, 1, 12, 345, 7, 220,\n" +
              "                     false, false, true, false, 0, null);\n" +
              "   ele.dispatchEvent(eo);\n" +
              "} else if (document.createEventObject) {" +
              "   var eo = document.createEventObject();\n" +
              "   ele.fireEvent('on" + e + "', eo);\n" +
              "}\n" +
              "return 0;\n");
   }


   /**
    * Set the internal {@link Reporter} object.
    *
    * @param rep  a {@link Reporter} object
    */

   public void setReporter(Reporter rep) {
      this.reporter = rep;
   }


   /**
    * Get the internal {@link Reporter} object.
    *
    * @return the {@link Reporter} object
    */

   public Reporter getReporter() {
      return this.reporter;
   }


   /**
    * Set the {@link WebDriver} for the browser to use.
    *
    * @param driver  the {@link WebDriver}
    */

   public void setDriver(WebDriver driver) {
      this.Driver = driver;
      logDriverCaps();
      driver.manage().timeouts().pageLoadTimeout(this.seleniumTimeout,
                                                 java.util.concurrent.TimeUnit.SECONDS);
   }

   /**
    * Get the current {@link WebDriver}.
    *
    * @return {@link WebDriver}
    */

   public WebDriver getDriver() {
      return this.Driver;
   }


   /**
    * When set, the browser bypasses java Alert and Confirm dialogs.
    *
    * @param alert  whether to bypass alerts
    */

   public abstract void alertHack(boolean alert);


   /**
    * Assert the specified text is found in the current page.
    *
    * @param value  the search string
    * @return whether the text was found
    */

   public boolean Assert(String value) {
      return this.reporter.Assert(value, this.getPageSource());
   }


   /**
    * Assert the specified text is found in a {@link WebElement}
    *
    * @param value   the search string
    * @param parent  the element to search
    * @return whether the text was found
    */

   public boolean Assert(String value, WebElement parent) {
      return this.reporter.Assert(value, parent.getText());
   }


   /**
    * Assert the specified text does not exist in the current page.
    *
    * @param value  the search string
    * @return true if the text was not found, false otherwise
    */

   public boolean AssertNot(String value) {
      return this.reporter.AssertNot(value, this.getPageSource());
   }


   /**
    * Assert the specified text does not exist in a {@link WebElement}
    *
    * @param value   the search string
    * @param parent  the element to search
    * @return true if the text was not found, false otherwise
    */

   public boolean AssertNot(String value, WebElement parent) {
      return this.reporter.AssertNot(value, parent.getText());
   }


   /**
    * Check the current page against the page assert list.
    *
    * @param whitelist  {@link VDDHash} with values to ignore
    */

   public boolean assertPage(HashMap<String,String> whitelist) {
      boolean result = false;

      if (this.asserter == null && this.assertPageFile != null) {
         try {
            this.asserter = new PageAsserter(this.assertPageFile,
                                             this.reporter);
         } catch (VDDException e) {
            this.reporter.exception(e);
         }
      }

      if (this.asserter != null) {
         this.asserter.assertPage(this.getPageSource(), whitelist);
      }

      return result;
   }


   /**
    * Load the page assert file.
    *
    * @param f  the file of page asserts
    */

   public void setAssertPageFile(File f) {
      this.assertPageFile = f;
      try {
         this.asserter = new PageAsserter(f, reporter);
      } catch (VDDException e) {
         this.reporter.exception(e);
      }
   }


   /**
    * Get page assert file.
    *
    * @return page assert file or null, if no file has been assigned
    */

   public File getAssertPageFile() {
      return this.assertPageFile;
   }
}
