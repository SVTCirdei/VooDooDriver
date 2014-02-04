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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import org.openqa.selenium.ElementNotVisibleException;
import org.openqa.selenium.WebElement;
import org.sugarcrm.voodoodriver.Event.Event;


/**
 * This is the heart of all of VooDooDriver.  This class handles
 * executing all of the SODA language commands in the web browser.
 *
 * @author trampus
 * @author Jon duSaint
 */

public class EventLoop implements Runnable {

   /**
    * List of events in this test.
    */

   private ArrayList<Event> testEvents;

   /**
    * Browser object for use by this test.
    */

   public Browser Browser;

   /**
    * Reporter object for use by this test.
    */

   public Reporter report;

   /**
    * VDD variables can have their values overridden by values passed
    * in on the command line or in the configuration file.  These are
    * referred to as &quot;hijacks&quot;.  This is a structure of
    * hijack key value pairs.
    *
    * XXX: Should probably be typed, e.g. HashMap<String,Object> or something.
    */

   public VDDHash hijacks;

   /**
    * Name of the current running test.
    */

   public String testName;

   /**
    * Strings to ignore during page assertion processing.
    */

   public HashMap<String,String> whitelist;

   /**
    * Stored, named HTML elements for use with DnD.
    */

   public HashMap<String,WebElement> elementStore;

   /**
    * List of loaded plugins.
    */

   public ArrayList<Plugin> plugins;

   /**
    * VDD variables.
    */

   public Vars vars;

   /**
    * File specified by a &lt;csv override=&quot;&quot;/&rt; event.
    */

   public File csvOverrideFile;

   /**
    * Handle to the browser window.
    */

   private String currentHWnd = null;

   /**
    * Amount of time, in seconds, to wait after switching windows.
    */

   private int attachTimeout = 0;

   /**
    * Timestamp that indicates to Test.runTest that this thread is
    * still alive.  If this is not updated before the expiry of the
    * watchdog timer, the test is considered a failure and this thread
    * is terminated.
    */

   private Date threadTime = null;

   /**
    * This thread.
    */

   private volatile Thread runner;

   /**
    * If true, this thread is to terminate.
    */

   private volatile Boolean threadStop = false;

   /**
    * Element search timeout.  VDD retries element finding for this
    * duration (in seconds) before giving up.
    */

   private int elementTimeout = 5;


   /**
    * Duration of the current wait event.
    */

   private long waitDuration = 0;


   /**
    * Instantiate EventLoop.
    *
    * @param events   list of events from the test file
    * @param config   VDD config info
    * @param reporter {@link Reporter}
    * @param vars  {@link Vars}
    * @param testName the current running test
    */

   public EventLoop(ArrayList<Event> events, VDDHash config, Reporter reporter,
                    Vars vars, String testName) {
      this.testEvents = events;
      this.report = reporter;
      this.vars = vars;
      this.testName = testName;

      this.Browser = (Browser)config.get("browser");
      this.hijacks = (VDDHash)config.get("hijack");;
      if (config.get("eventtimeout") != null) {
         this.elementTimeout = (Integer)config.get("eventtimeout");
      }
      this.whitelist = new HashMap<String,String>();
      this.elementStore = new HashMap<String,WebElement>();

      this.plugins = new ArrayList<Plugin>();
      @SuppressWarnings("unchecked")
         ArrayList<Plugin> p = (ArrayList<Plugin>)config.get("plugin");
      if (p != null) {
         this.plugins.addAll(p);
      }

      /* Create an initial timestamp. */
      this.vars.put("stamp",
                    new SimpleDateFormat("yyMMdd_hhmmss").format(new Date()));

      SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy");
      this.vars.put("currentdate", df.format(new Date()));

      this.setCurrentHWND(this.Browser.getDriver().getWindowHandle());

      this.threadTime = new Date();
      this.runner = new Thread(this, "EventLoop-Thread");
      runner.start();
   }


   /**
    * Set the attach timeout.
    *
    * The attach timeout is the time in seconds after which the Attach
    * event will give up retrying to find the window to attach to.
    *
    * @param timeout  number of seconds to wait after window switch
    */

   public void setAttachTimeout(int timeout) {
      this.attachTimeout = timeout;
   }


   /**
    * Retrieve the attach timeout.
    *
    * @return the attach timeout
    */

   public int getAttachTimeout() {
      return this.attachTimeout;
   }


   /**
    * Get the element search timeout.
    *
    * @return the element search timeout
    */

   public int getElementTimeout() {
      return this.elementTimeout;
   }


   /**
    * Get the duration of a wait event in progress.
    *
    * <p>This is 0 unless a wait event is in progress, in which case
    * it's set to the length of the wait in milliseconds.  This
    * information is used by <code>Test.runTest</code> to extend the
    * watchdog timeout by the duration of this wait.</p>
    *
    * @return  current wait duration
    */

   public long getWaitDuration() {
      return this.waitDuration;
   }

   /**
    * Set the duration of a wait event.
    *
    * <p>The wait event updates EventLoop's value of
    * <code>waitDuration</code> to provide <code>Test.runTest</code>
    * with the duration of the current wait in order to prevent the
    * watchdog timer from expiring during long waits.</p>
    *
    * @param duration  current wait duration
    */

   public void setWaitDuration(long duration) {
      this.waitDuration = duration;
   }


   /**
    * Check to see if a browser window exists.
    *
    * @param hwnd  window handle
    * @return whether the window exists
    */

   private boolean windowExists(String hwnd) {
      Set<String> windows = null;

      try {
         windows = this.Browser.getDriver().getWindowHandles();
      } catch (org.openqa.selenium.WebDriverException e) {
         /*
          * When running the IE driver, if the window is closed, an
          * exception is thrown.
          */
         return false;
      }

      for (int i = 0; i < windows.size(); i++) {
         if (hwnd.equals(windows.toArray()[i].toString())) {
            return true;
         }
      }

      return false;
   }


   /**
    * Set the active browser window for VDD to use.
    *
    * @param hwnd {@link String}
    */

   public void setCurrentHWND(String hwnd) {
      this.currentHWnd = hwnd;
   }


   /**
    * Return whether this thread is still active.
    *
    * @return true if the thread is alive
    */

   public boolean isAlive() {
      return this.runner.isAlive();
   }


   /**
    * Indicate that this thread should terminate.
    */

   public void stop() {
      synchronized (this.threadStop) {
         this.threadStop = true;
         this.runner.interrupt();
      }
   }


   /**
    * Return whether the thread has been told to stop.
    *
    * @return whether the thread has been told to stop
    */

   public boolean isStopped() {
      boolean result = false;

      synchronized (this.threadStop) {
         result = this.threadStop;
      }

      return result;
   }


   /**
    * Update this EventLoop's thread time.
    */

   public void updateThreadTime() {
      synchronized (this.threadTime) {
         this.threadTime = new Date();
      }
   }


   /**
    * Return the current thread time.
    *
    * @return current thread time
    */

   public Date getThreadTime() {
      Date tmp = null;

      synchronized (this.threadTime) {
         tmp = this.threadTime;
      }

      return tmp;
   }


   /**
    * Thread entry point.
    *
    * Initialize thread state and run the events in this test file.
    */

   public void run() {
      this.report.log("Thread Running...");
      this.threadTime = new Date();

      this.firePlugin(null, PluginEvent.BEFORETEST);

      processEvents(this.testEvents, null);

      this.firePlugin(null, PluginEvent.AFTERTEST);
   }


   /**
    * Run a sequence of events.
    *
    * @param events  {@link ArrayList} of {@link Event} from the test script
    * @param parent  parent element if process child events
    */

   public void processEvents(ArrayList<Event> events, WebElement parent) {
      for (Event event: events) {
         if (isStopped()) {
            break;
         }

         handleSingleEvent(event, parent);
      }
   }


   /**
    * Execute one {@link Event}.
    *
    * @param event   the {@link Event} to execute
    * @param parent  parent element, if currently executing children
    */

   private void handleSingleEvent(Event event, WebElement parent) {
      String eventName = event.getName();

      this.report.log(eventName + " event started...");
      this.updateThreadTime();
      event.setEventLoop(this);
      event.setParent(parent);

      try {
         event.execute();
         if (event.hasChildren()) {
            processEvents(event.getChildren(), event.getElement());
         }
         event.afterChildren();
      } catch (org.sugarcrm.voodoodriver.Event.StopEventException e) {
         /* Not an error */
      } catch (ElementNotVisibleException e) {
         String s = "Element is not visible";
         if (event.required()) {
            this.report.error(s);
         } else {
            this.report.log(s + ", but required = false");
         }
      } catch (VDDException e) {
         Throwable cause = e.getCause();

         if (cause == null) {
            cause = e;
         }

         this.report.exception("Exception during event execution", cause);
      }

      this.updateThreadTime();
      this.report.log(eventName + " event finished.");

      this.firePlugin(PluginEvent.AFTEREVENT);
   }


   /**
    * Execute a plugin if the current event matches.
    *
    * @param type  the current plugin event
    */

   public void firePlugin(PluginEvent type) {
      this.firePlugin(null, type);
   }


   /**
    * Execute a plugin if the current event matches.
    *
    * @param event  the current executing event
    * @param type  the current plugin event
    */

   public void firePlugin(Event event, PluginEvent type) {
      PluginData data;
      String element = null;

      if (this.plugins.size() == 0) {
         return;
      }

      if (!this.windowExists(this.currentHWnd)) {
         this.report.log("Browser window closed. Skipping plugin execution.");
         return;
      }

      if (event != null) {
         element = event.getName().toLowerCase();
      }

      data = new PluginData();
      if (event != null) {
         data.setElement(event.getElement());
      }
      data.setBrowser(this.Browser);
      data.setVars(this.vars);
      data.setHijacks(this.hijacks);
      data.setTestName(this.testName);

      for (Plugin plugin: this.plugins) {
         if ((event != null && plugin.matches(element, type)) ||
             (event == null && plugin.matches(type))) {
            plugin.execute(data, this.report);
         }
      }
   }
}
