/*
 * Copyright 2012 SugarCRM Inc.
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

package org.sugarcrm.voodoodriver.Event;

import org.openqa.selenium.NoSuchFrameException;
import org.openqa.selenium.WebElement;
import org.sugarcrm.voodoodriver.VDDException;
import org.w3c.dom.Element;

/**
 * The frame event.
 *
 * <p>The frame event runs against IFRAME elements.  Thus, the name of
 * this class is Iframe rather than Frame.  Attributes for frame
 * are:</p>
 *
 * <dl>
 *   <dt>index</dt><dd>Zero-based index of iframes on the page.</dd>
 *   <dt>name</dt><dd>Value of the iframe's name attribute.</dd>
 *   <dt>id</dt><dd>Synonym for name</dd>
 *   <dt>xpath</dt><dd>XPath to the iframe</dd>
 * </dl>
 *
 * @author Jon duSaint
 */

class Iframe extends Event {


   /**
    * Instantiate an iframe event.
    *
    * @param e  the event as specified in the test script
    * @throws VDDException if event instantiation fails
    */

   public Iframe(Element e) throws VDDException {
      super(e);
   }


   /**
    * Switch to an iframe by its index.
    *
    * @throws VDDException if the specified index is not an integer
    */

   private void switchByIndex() throws VDDException {
      String is = replaceString((String)this.selectors.get("index"));
      int index = 0;

      try {
         index = Integer.valueOf(is);
      } catch (NumberFormatException e) {
         throw new VDDException("Invalid index '" + is + "'", e);
      }

      log("Switching to iframe by index '" + index + "'.");
      try {
         this.eventLoop.Browser.getDriver().switchTo().frame(index);
      } catch (NoSuchFrameException e) {
         this.eventLoop.report.error("No iframe with index '" +
                                     index + "' found");
         throw new StopEventException();
      }
   }


   /**
    * Switch to an iframe by its name.
    *
    * @param sel  selector key with the frame name
    */

   private void switchByName(String sel) {
      String name = replaceString((String)this.selectors.get(sel));

      log("Switching to iframe by name '" + name + "'");
      try {
         this.eventLoop.Browser.getDriver().switchTo().frame(name);
      } catch (NoSuchFrameException e) {
         this.eventLoop.report.error("No iframe with name '" +
                                     name + "' found");
         throw new StopEventException();
      }
   }


   /**
    * Switch to an iframe by its xpath.
    *
    * @throws VDDException if the frame doesn't exist
    */

   private void switchByXPath() {
      ElementFinder ef =
         new ElementFinder(this.eventLoop.Browser,
                           this.selectors,
                           this.eventLoop.report,
                           this.eventLoop.getElementTimeout(),
                           this.parent,
                           this.getName().toLowerCase());
      WebElement element = ef.findElement();

      try {
         this.eventLoop.Browser.getDriver().switchTo().frame(element);
         return;
      } catch (NoSuchFrameException e) {
         /* Unlikely to occur. */
      } catch (NullPointerException e) {
         /* Iframe was not located by ElementFinder. */
      }

      throw new StopEventException();
   }


   /**
    * Run the iframe event.
    *
    * @throws VDDException if execution is unsuccessful
    */

   public void execute() throws VDDException {
      if (this.selectors.containsKey("index")) {
         switchByIndex();
      } else if (this.selectors.containsKey("id")) {
         switchByName("id");
      } else if (this.selectors.containsKey("name")) {
         switchByName("name");
      } else if (this.selectors.containsKey("xpath")) {
         switchByXPath();
      } else {
         throw new VDDException("Missing index, id, name, or xpath attribute.");
      }
   }


   /**
    * Switch back to the default iframe.
    *
    * @throws VDDException if switching back fails
    */

   @Override
   public void afterChildren() throws VDDException {
      try {
         log("Switching back to default iframe.");
         this.eventLoop.Browser.getDriver().switchTo().defaultContent();
      } catch (NoSuchFrameException e) {
         throw new VDDException("Unable to switch back to default iframe", e);
      }
   }

}
