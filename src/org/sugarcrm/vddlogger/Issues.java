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

import java.util.HashMap;

public class Issues {

   private HashMap<String, Integer> errors = null;
   private HashMap<String, Integer> wanrings = null;
   private HashMap<String, Integer> exceptions = null;

   public Issues () {

      this.errors = new HashMap<String, Integer>();
      this.wanrings = new HashMap<String, Integer>();
      this.exceptions = new HashMap<String, Integer>();

   }

   public HashMap<String, HashMap<String, Integer>> getData() {
      HashMap<String, HashMap<String, Integer>> data = new HashMap<String, HashMap<String,Integer>>();

      data.put("errors", this.errors);
      data.put("warnings", this.wanrings);
      data.put("exceptions", this.exceptions);

      return data;
   }

   public void addException(String str) {
      if (this.exceptions.containsKey(str)) {
         Integer tmp = this.exceptions.get(str);
         tmp += 1;
         this.exceptions.put(str, tmp);
      } else {
         this.exceptions.put(str, 1);
      }
   }

   public void addWarning(String str) {
      if (this.wanrings.containsKey(str)) {
         Integer tmp = this.wanrings.get(str);
         tmp += 1;
         this.wanrings.put(str, tmp);
      } else {
         this.wanrings.put(str, 1);
      }
   }

   public void addError(String str) {
      if (this.errors.containsKey(str)) {
         Integer tmp = this.errors.get(str);
         tmp += 1;
         this.errors.put(str, tmp);
      } else {
         this.errors.put(str, 1);
      }
   }

   public void appendIssues(Issues issues) {
      addIssue(issues.errors, this.errors);
      addIssue(issues.wanrings, this.wanrings);
      addIssue(issues.exceptions, this.exceptions);
   }

   private void addIssue(HashMap<String, Integer> src, HashMap<String, Integer> dst) {
      String[] keys = src.keySet().toArray(new String[0]);

      for (int i = 0; i <= keys.length -1; i++) {
         if (!dst.containsKey(keys[i])) {
            dst.put(keys[i], src.get(keys[i]));
         } else {
            int srcCount = src.get(keys[i]);
            int dstCount = dst.get(keys[i]);
            dst.put(keys[i], (srcCount + dstCount));
         }
      }
   }
}