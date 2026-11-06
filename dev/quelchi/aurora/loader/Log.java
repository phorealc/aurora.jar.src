/*    */ package dev.quelchi.aurora.loader;
/*    */ 
/*    */ import org.slf4j.Logger;
/*    */ import org.slf4j.LoggerFactory;
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ final class Log
/*    */ {
/* 17 */   private static final Logger LOG = LoggerFactory.getLogger("Aurora/Loader");
/*    */   
/*    */   static void info(String message) {
/* 20 */     LOG.info(message);
/*    */   }
/*    */   
/*    */   static void warn(String message) {
/* 24 */     LOG.warn(message);
/*    */   }
/*    */   
/*    */   static void error(String message, Throwable cause) {
/* 28 */     if (cause == null) { LOG.error(message); }
/* 29 */     else { LOG.error(message, cause); }
/*    */   
/*    */   }
/*    */ }


/* Location:              C:\Users\rapha\Downloads\aurora.jar!\dev\quelchi\aurora\loader\Log.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */