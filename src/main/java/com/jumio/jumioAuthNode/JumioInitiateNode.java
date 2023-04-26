/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2017-2018 ForgeRock AS.
 */

package com.jumio.jumioAuthNode;

import static com.jumio.jumioAuthNode.JumioConstants.ACCOUNT_ID;
import static com.jumio.jumioAuthNode.JumioConstants.AQUISITION_STATUS;
import static com.jumio.jumioAuthNode.JumioConstants.CUSTOMER_INTERNAL_REFERENCE;
import static com.jumio.jumioAuthNode.JumioConstants.ERROR_OUTCOME;
import static com.jumio.jumioAuthNode.JumioConstants.ERROR_URL;
import static com.jumio.jumioAuthNode.JumioConstants.FALSE_OUTCOME;
import static com.jumio.jumioAuthNode.JumioConstants.SUCCESS_URL;
import static com.jumio.jumioAuthNode.JumioConstants.TRUE_OUTCOME;
import static com.jumio.jumioAuthNode.JumioConstants.USER_REFERENCE;
import static com.jumio.jumioAuthNode.JumioConstants.WORKFLOW_EXECUTION_ID;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.openam.auth.node.api.SharedStateConstants;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.forgerock.util.i18n.PreferredLocales;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.authentication.spi.RedirectCallback;
import com.sun.identity.sm.SMSException;


@Node.Metadata(outcomeProvider = JumioInitiateNode.JumioInitiateOutcomeProvider.class, configClass = JumioInitiateNode.Config.class, tags = {
		"marketplace", "trustnetwork" })
public class JumioInitiateNode extends AbstractDecisionNode {

	private final Logger logger = LoggerFactory.getLogger(JumioInitiateNode.class);
	private final JumioService serviceConfig;
	private String loggerPrefix = "[Jumio Initiate][Marketplace] ";

	/**
	 * Configuration for the node.
	 */
	public interface Config {

	}

	/**
	 * Create the node using Guice injection. Just-in-time bindings can be used to
	 * obtain instances of other classes from the plugin.
	 **/
	@Inject
	public JumioInitiateNode(@Assisted Realm realm, AnnotatedServiceRegistry serviceRegistry)
			throws NodeProcessException {
		
		//If the service does not exist, the journey will go into an endless loop and never be able to load the first node,
		//regardless of what that node is
		
		try {
			this.serviceConfig = serviceRegistry.getRealmSingleton(JumioService.class, realm).get();
		} catch (SSOException | SMSException e) {
			throw new NodeProcessException(e);
		}
		if (StringUtils.isEmpty(serviceConfig.serverUrl().toString()) || StringUtils.isEmpty(serviceConfig.token())
				|| StringUtils.isEmpty(String.valueOf(serviceConfig.secret()))
				|| StringUtils.isEmpty(serviceConfig.customerInternalReference())
				|| StringUtils.isEmpty(serviceConfig.merchantReportingCriteria())) {
			throw new NodeProcessException("One or values in the Jumio Service is empty");
		}
	}

	@Override
	public Action process(TreeContext context) {
		HttpURLConnection conn = null;
		try {
			logger.debug(loggerPrefix + "Started");
			Map<String, List<String>> parameters = context.request.parameters;
			NodeState ns = context.getStateFor(this);
			
			String streamToString, redirectURL, userReference;
			userReference = ns.get(SharedStateConstants.USERNAME).asString();

			if (parameters.containsKey(AQUISITION_STATUS) && parameters.containsKey(CUSTOMER_INTERNAL_REFERENCE)
					&& parameters.containsKey(WORKFLOW_EXECUTION_ID)) {
				if (ns.isDefined(AQUISITION_STATUS) && ns.isDefined(CUSTOMER_INTERNAL_REFERENCE)
						&& ns.isDefined(WORKFLOW_EXECUTION_ID)) {
					// We have looped back from a unsuccessful ID proofing attempt, remove
					// sharedState and continue
					ns.remove(AQUISITION_STATUS);
					ns.remove(CUSTOMER_INTERNAL_REFERENCE);
					ns.remove(WORKFLOW_EXECUTION_ID);
				} else {
					// We have returned from redirect, store Jumio data in shared state and go to
					// next node
					ns.putShared(AQUISITION_STATUS, parameters.get(AQUISITION_STATUS).get(0));
					ns.putShared(WORKFLOW_EXECUTION_ID, parameters.get(WORKFLOW_EXECUTION_ID).get(0));
					ns.putShared(CUSTOMER_INTERNAL_REFERENCE, userReference);
					ns.putShared(ACCOUNT_ID, parameters.get(ACCOUNT_ID).get(0));

					if (logger.isInfoEnabled()) {
						logger.info(loggerPrefix + "Returned from redirect.  Account ID: "
								+ parameters.get(ACCOUNT_ID).get(0) + " Aquisition Status: "
								+ parameters.get(AQUISITION_STATUS).get(0) + " Workflow Execution ID: "
								+ parameters.get(WORKFLOW_EXECUTION_ID).get(0));
					}
					return Action.goTo(TRUE_OUTCOME).build();
				}
			}

			URL url;

			url = new URL("https://account." + serviceConfig.serverUrl().toString() + "/api/v1/accounts");

			String accessToken = JumioUtils.getAccessToken(serviceConfig);

			conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("User-Agent", "Jumio ForgeRock/1.1.2");
			conn.setRequestProperty("Authorization", "Bearer " + accessToken);

			JSONObject bodyObject = new JSONObject();
			JSONObject bodyObject2 = new JSONObject();
			bodyObject.put(CUSTOMER_INTERNAL_REFERENCE, serviceConfig.customerInternalReference());
			bodyObject.put(USER_REFERENCE, userReference);

			bodyObject2.put(SUCCESS_URL, serviceConfig.redirectURI());
			bodyObject2.put(ERROR_URL, serviceConfig.redirectURI());
			bodyObject.put("web", bodyObject2);
			JSONObject bodyObject3 = new JSONObject();
			bodyObject3.put("key", 10011); //TODO make this a key
			bodyObject.put("workflowDefinition", bodyObject3);
			OutputStreamWriter wr;
			int responseCode;

			wr = new OutputStreamWriter(conn.getOutputStream());
			wr.write(bodyObject.toString());
			wr.flush();
			streamToString = JumioUtils.convertStreamToString(conn.getInputStream());
			responseCode = conn.getResponseCode();

			if (responseCode == 200) {
				JSONObject jo = new JSONObject(streamToString);
				// if successfully submitted, move the files to completed.

				redirectURL = (String) jo.getJSONObject("web").get("href");
			} else if (responseCode == 403) {
				throw new NodeProcessException("403: Invalid Credentials");
			} else if (responseCode == 400) {
				throw new NodeProcessException("400: Bad request.");

			} else {
				throw new NodeProcessException("Unknown.");
			}

			RedirectCallback redirectCallback = new RedirectCallback(redirectURL, null, "GET");
			redirectCallback.setTrackingCookie(true);
			return Action.send(redirectCallback).build();
		} catch (Exception ex) {
			logger.error(loggerPrefix + "Exception occurred: " + ex.getStackTrace());
			context.getStateFor(this).putShared(loggerPrefix + "Exception", new Date() + ": " + ex.getMessage());
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);
			context.getStateFor(this).putShared(loggerPrefix + "StackTracke", new Date() + ": " + sw.toString());
			return Action.goTo(JumioConstants.ERROR_OUTCOME).build();
		}
		finally {
			try {
				if (conn!=null)
					conn.disconnect();
			}
			catch(Exception e) {
				//Do nothing
			}
		}
	}

	/**
	 * Defines the possible outcomes from this node.
	 */
	public static class JumioInitiateOutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
		@Override
		public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
			return ImmutableList.of(
					new Outcome(TRUE_OUTCOME, TRUE_OUTCOME),
					new Outcome(FALSE_OUTCOME, FALSE_OUTCOME),
					new Outcome(ERROR_OUTCOME, ERROR_OUTCOME)

			);
		}
	}
}