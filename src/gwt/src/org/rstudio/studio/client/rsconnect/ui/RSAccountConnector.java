/*
 * RSAccountConnector.java
 *
 * Copyright (C) 2009-15 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.rsconnect.ui;

import org.rstudio.core.client.Size;
import org.rstudio.core.client.widget.OperationWithInput;
import org.rstudio.core.client.widget.ProgressOperationWithInput;
import org.rstudio.core.client.widget.ProgressIndicator;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.satellite.SatelliteManager;
import org.rstudio.studio.client.rsconnect.model.NewRSConnectAccountResult;
import org.rstudio.studio.client.rsconnect.model.NewRSConnectAccountResult.AccountType;
import org.rstudio.studio.client.rsconnect.model.RSConnectAuthParams;
import org.rstudio.studio.client.rsconnect.model.RSConnectAuthUser;
import org.rstudio.studio.client.rsconnect.model.RSConnectPreAuthToken;
import org.rstudio.studio.client.rsconnect.model.RSConnectServerInfo;
import org.rstudio.studio.client.rsconnect.model.RSConnectServerOperations;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.server.Void;
import org.rstudio.studio.client.workbench.model.Session;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class RSAccountConnector
{
   // possible results of attempting to connect an account
   enum AccountConnectResult
   {
      Incomplete,
      Successful,
      Failed
   }

   @Inject
   public RSAccountConnector(SatelliteManager satelliteManager,
         RSConnectServerOperations server,
         GlobalDisplay display,
         Session session)
   {
      satelliteManager_ = satelliteManager;
      server_ = server;
      display_ = display;
      session_ = session;
      exportAuthClosedCallback();
   }
   
   public void showAccountWizard(
         final OperationWithInput<Boolean> onCompleted)
   {
      RSConnectAccountWizard wizard = new RSConnectAccountWizard(
            session_.getSessionInfo(),
            new ProgressOperationWithInput<NewRSConnectAccountResult>()
      {
         @Override
         public void execute(NewRSConnectAccountResult input,
               final ProgressIndicator indicator)
         {
            connectNewAccount(input, indicator, 
                  new OperationWithInput<AccountConnectResult>()
            {
               @Override
               public void execute(AccountConnectResult input)
               {
                  if (input == AccountConnectResult.Failed)
                  {
                     // the connection failed--take down the dialog entirely
                     // (we do this when retrying doesn't make sense)
                     onCompleted.execute(false);
                     indicator.onCompleted();
                  }
                  else if (input == AccountConnectResult.Incomplete)
                  {
                     // the connection didn't finish--take down the progress and
                     // allow retry
                     indicator.clearProgress();
                  }
                  else if (input == AccountConnectResult.Successful)
                  {
                     // successful account connection--mark finished
                     onCompleted.execute(true);
                     indicator.onCompleted();
                  }
               }
            });
         }
      });
      wizard.showModal();
   }

   public void connectNewAccount(
         NewRSConnectAccountResult result,
         ProgressIndicator indicator,
         OperationWithInput<AccountConnectResult> onConnected)
   {
      if (result.getAccountType() == AccountType.RSConnectCloudAccount)
      {
         connectCloudAccount(result, indicator, onConnected);
      }
      else
      {
         connectLocalAccount(result, indicator, onConnected);
      }
   }
   
   public void connectCloudAccount(
         final NewRSConnectAccountResult result,
         final ProgressIndicator indicator,
         final OperationWithInput<AccountConnectResult> onConnected)
   {
      // get command and substitute rsconnect for shinyapps
      final String cmd = result.getCloudSecret().replace("shinyapps::", 
                                                         "rsconnect::");
      if (!cmd.startsWith("rsconnect::setAccountInfo"))
      {
         display_.showErrorMessage("Error Connecting Account", 
               "The pasted command should start with " + 
               "rsconnect::setAccountInfo. If you're having trouble, try " + 
               "connecting your account manually; type " +
               "?rsconnect::setAccountInfo at the R console for help.");
         onConnected.execute(AccountConnectResult.Incomplete);
      }
      indicator.onProgress("Connecting account...");
      server_.connectRSConnectAccount(cmd, 
            new ServerRequestCallback<Void>()
      {
         @Override
         public void onResponseReceived(Void v)
         {
            onConnected.execute(AccountConnectResult.Successful);
         }

         @Override
         public void onError(ServerError error)
         {
            display_.showErrorMessage("Error Connecting Account",  
                  "The command '" + cmd + "' failed. You can set up an " + 
                  "account manually by using rsconnect::setAccountInfo; " +
                  "type ?rsconnect::setAccountInfo at the R console for " +
                  "more information.");
            onConnected.execute(AccountConnectResult.Failed);
         }
      });
   }

   public void connectLocalAccount(
         final NewRSConnectAccountResult result,
         final ProgressIndicator indicator,
         final OperationWithInput<AccountConnectResult> onConnected)
   {
      indicator.onProgress("Checking server connection...");
      server_.validateServerUrl(result.getServerUrl(), 
            new ServerRequestCallback<RSConnectServerInfo>()
      {
         @Override
         public void onResponseReceived(RSConnectServerInfo info)
         {
            if (info.isValid()) 
            {
               getPreAuthToken(info, indicator, onConnected);
            }
            else
            {
               display_.showErrorMessage("Server Validation Failed", 
                     "The URL '" + result.getServerUrl() + "' does not " +
                     "appear to belong to a valid server. Please double " +
                     "check the URL, and contact your administrator if " + 
                     "the problem persists.\n\n" +
                     info.getMessage());
               onConnected.execute(AccountConnectResult.Incomplete);
            }
         }

         @Override
         public void onError(ServerError error)
         {
            display_.showErrorMessage("Error Connecting Account", 
                  "The server couldn't be validated. " + 
                   error.getMessage());
            onConnected.execute(AccountConnectResult.Incomplete);
         }
      });
   }
   
   private void getPreAuthToken(
         final RSConnectServerInfo serverInfo,
         final ProgressIndicator indicator,
         final OperationWithInput<AccountConnectResult> onConnected)
   {
      indicator.onProgress("Setting up an account...");
      server_.getPreAuthToken(serverInfo.getName(), 
            new ServerRequestCallback<RSConnectPreAuthToken>()
      {
         @Override
         public void onResponseReceived(final RSConnectPreAuthToken token)
         {
            indicator.onProgress("Waiting for authentication...");

            // set up pending state -- these will be used to complete the wizard
            // once the user finishes using the wizard to authenticate
            pendingAuthToken_ = token;
            pendingServerInfo_ = serverInfo;
            pendingAuthIndicator_ = indicator;
            pendingOnConnected_ = onConnected;
            
            // open the satellite window 
            satelliteManager_.openSatellite(
                  RSConnectAuthSatellite.NAME, 
                  RSConnectAuthParams.create(serverInfo, token), 
                  new Size(700, 800));
            
            // we'll finish auth automatically when the satellite closes, but we
            // also want to close it ourselves when we detect that auth has
            // completed
            pollForAuthCompleted();
         }

         @Override
         public void onError(ServerError error)
         {
            display_.showErrorMessage("Error Connecting Account", 
                  "The server appears to be valid, but rejected the " + 
                  "request to authorize an account.\n\n"+
                  serverInfo.getInfoString() + "\n" +
                  error.getMessage());
            onConnected.execute(AccountConnectResult.Incomplete);
         }
      });
   }
   
   private void pollForAuthCompleted()
   {
      // TODO: check every ~1s to see if token has become valid; auto-close the
      // window if it has
   }
   
   private void notifyAuthClosed()
   {
      if (pendingAuthToken_ != null &&
          pendingServerInfo_ != null &&
          pendingAuthIndicator_ != null &&
          pendingOnConnected_ != null)
      {
         onAuthCompleted(pendingServerInfo_, 
               pendingAuthToken_, 
               pendingAuthIndicator_, 
               pendingOnConnected_);
      }
      pendingAuthToken_ = null;
      pendingServerInfo_ = null;
      pendingAuthIndicator_ = null;
      pendingOnConnected_ = null;
   }

   private void onAuthCompleted(
         final RSConnectServerInfo serverInfo,
         final RSConnectPreAuthToken token,
         final ProgressIndicator indicator,
         final OperationWithInput<AccountConnectResult> onConnected)
   {
      indicator.onProgress("Validating account...");
      server_.getUserFromToken(serverInfo.getUrl(), token, 
            new ServerRequestCallback<RSConnectAuthUser>()
      {
         @Override 
         public void onResponseReceived(RSConnectAuthUser user)
         {
            if (!user.isValidUser())
            {
               display_.showErrorMessage("Account Not Connected", 
                     "Authentication failed. If you did not cancel " +
                     "authentication, try again, or contact your server " +
                     "administrator for assistance.");
               onConnected.execute(AccountConnectResult.Incomplete);
            }
            else
            {
               onUserAuthVerified(serverInfo, token, user, 
                                  indicator, onConnected);
            }
         }

         @Override
         public void onError(ServerError error)
         {
            display_.showErrorMessage("Account Validation Failed", 
                  "RStudio failed to determine whether the account was " +
                  "valid. Try again; if the error persists, contact your " +
                  "server administrator.\n\n" +
                  serverInfo.getInfoString() + "\n" +
                  error.getMessage());
            onConnected.execute(AccountConnectResult.Incomplete);
         }
      });
   }
   
   private void onUserAuthVerified(
         final RSConnectServerInfo serverInfo,
         final RSConnectPreAuthToken token,
         final RSConnectAuthUser user,
         final ProgressIndicator indicator,
         final OperationWithInput<AccountConnectResult> onConnected)
   {
      indicator.onProgress("Adding account...");
      String accountName;
      if (user.getUsername().length() > 0) 
      {
         // if we have a username already, just use it 
         accountName = user.getUsername();
      }
      else
      {
         // if we don't have any username, guess one based on user's given name
         // on the server
         accountName = (user.getFirstName().substring(0, 1) + 
               user.getLastName()).toLowerCase();
      }
       
      server_.registerUserToken(serverInfo.getName(), accountName, 
            user.getId(), token, new ServerRequestCallback<Void>()
      {
         @Override
         public void onResponseReceived(Void result)
         {
            onConnected.execute(AccountConnectResult.Successful);
         }

         @Override
         public void onError(ServerError error)
         {
            display_.showErrorMessage("Account Connect Failed", 
                  "Your account was authenticated successfully, but could " +
                  "not be connected to RStudio. Make sure your installation " +
                  "of the 'rsconnect' package is correct for the server " + 
                  "you're connecting to.\n\n" +
                  serverInfo.getInfoString() + "\n" +
                  error.getMessage());
            onConnected.execute(AccountConnectResult.Failed);
         }
      });
   }

   private final native void exportAuthClosedCallback()/*-{
      var registry = this;     
      $wnd.notifyAuthClosed = $entry(
         function() {
            registry.@org.rstudio.studio.client.rsconnect.ui.RSAccountConnector::notifyAuthClosed()();
         }
      ); 
   }-*/;

   private final GlobalDisplay display_;
   private final RSConnectServerOperations server_;
   private final Session session_;
   private final SatelliteManager satelliteManager_;
   
   private RSConnectPreAuthToken pendingAuthToken_;
   private RSConnectServerInfo pendingServerInfo_;
   private ProgressIndicator pendingAuthIndicator_;
   private OperationWithInput<AccountConnectResult> pendingOnConnected_;
}
