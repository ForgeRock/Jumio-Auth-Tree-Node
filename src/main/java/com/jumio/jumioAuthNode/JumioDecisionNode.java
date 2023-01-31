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
import static com.jumio.jumioAuthNode.JumioConstants.ATTRIBUTES;
import static com.jumio.jumioAuthNode.JumioConstants.DECISION;
import static com.jumio.jumioAuthNode.JumioConstants.ERROR_OUTCOME;
import static com.jumio.jumioAuthNode.JumioConstants.NOT_EXECUTED;
import static com.jumio.jumioAuthNode.JumioConstants.OUTCOME;
import static com.jumio.jumioAuthNode.JumioConstants.PASSED;
import static com.jumio.jumioAuthNode.JumioConstants.PENDING_OUTCOME;
import static com.jumio.jumioAuthNode.JumioConstants.REJECTED;
import static com.jumio.jumioAuthNode.JumioConstants.STATUS;
import static com.jumio.jumioAuthNode.JumioConstants.TYPE;
import static com.jumio.jumioAuthNode.JumioConstants.UID;
import static com.jumio.jumioAuthNode.JumioConstants.USERNAME;
import static com.jumio.jumioAuthNode.JumioConstants.USER_INFO;
import static com.jumio.jumioAuthNode.JumioConstants.USER_NAMES;
import static com.jumio.jumioAuthNode.JumioConstants.WARNING;
import static com.jumio.jumioAuthNode.JumioConstants.WORKFLOW_EXECUTION;
import static com.jumio.jumioAuthNode.JumioConstants.WORKFLOW_EXECUTION_ID;
import static com.jumio.jumioAuthNode.JumioConstants.INITIATED;
import static com.jumio.jumioAuthNode.JumioConstants.ACQUIRED;
import static com.jumio.jumioAuthNode.JumioConstants.PROCESSED;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.forgerock.util.i18n.PreferredLocales;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.sm.SMSException;

@Node.Metadata(outcomeProvider = JumioDecisionNode.JumioDecisionOutcomeProvider.class, configClass = JumioDecisionNode.Config.class, tags = {
		"marketplace", "trustnetwork" })
public class JumioDecisionNode extends AbstractDecisionNode {

	private final Logger logger = LoggerFactory.getLogger(JumioDecisionNode.class);
	private final JumioService serviceConfig;
	private final Config config;
	private String loggerPrefix = "[Jumio Decision Node][Marketplace] ";

	/**
	 * Configuration for the node.
	 */
	public interface Config {

		@Attribute(order = 100)
		Map<String, String> cfgAccountMapperConfiguration();

	}

	/**
	 * Create the node using Guice injection. Just-in-time bindings can be used to
	 * obtain instances of other classes from the plugin.
	 **/
	@Inject
	public JumioDecisionNode(@Assisted Config config, @Assisted Realm realm, AnnotatedServiceRegistry serviceRegistry)
			throws NodeProcessException {
		this.config = config;
		try {
			this.serviceConfig = serviceRegistry.getRealmSingleton(JumioService.class, realm).get();
		} catch (SSOException | SMSException e) {
			throw new NodeProcessException(e);
		}
	}

	private String checkStatus(String wfExID, String acctID) throws Exception {
		logger.info(loggerPrefix + "Entered checkStatus");
		JSONObject jsonObj = getRetrievalMessage("https://retrieval." + serviceConfig.serverUrl().toString()
				+ "/api/v1/accounts/" + acctID + "/workflow-executions/" + wfExID + "/status");
		logger.info(loggerPrefix + "About to call status");
		String status;
		status = (String) jsonObj.getJSONObject(WORKFLOW_EXECUTION).get(STATUS);
		status = status.replaceAll("\"", "");
		logger.info(loggerPrefix + "Got Status.  And here it is: " + status);
		return status;

	}

	/**
	 * Retrieves the transaction results with the extracted data in a JSON object.
	 */
	private JSONObject getVerificationResults(String wfExID, String acctID) throws Exception {
		JSONObject result = getRetrievalMessage("https://retrieval." + serviceConfig.serverUrl().toString()
				+ "/api/v1/accounts/" + acctID + "/workflow-executions/" + wfExID);


		//JSONObject jsonObj = getRetrievalMessage("https://retrieval." + serviceConfig.serverUrl().toString() + "/api/v1/accounts/" + acctID + "/workflow-executions/" + wfExID);

		/*
		result = jsonObj.getJSONObject("document");
		// Add the customerid, merchantReportingCriteria, merchantScanReference
		result.put(SCAN_REFERENCE, wfExID);
		JSONObject t = jsonObj.getJSONObject("transaction");
		result.put("customerId", t.getString("customerId"));

		// Check the ID verification and return if not APPROVED_VERIFIED with right
		// outcome.
		String status = result.getString("status");
		if (status.equalsIgnoreCase("DENIED_FRAUD")) {
			result.put(OUTCOME, REJECTED);
			return result;
		} else if (status.equalsIgnoreCase(UNSUPPORTED_ID_TYPE) || status.equalsIgnoreCase(UNSUPPORTED_ID_COUNTRY)) {
			result.put(OUTCOME, WARNING);
			return result;

		} 

		// Now check if selfie matches image on the ID.
		JSONObject v = (JSONObject) jsonObj.get(VERIFICATION);
		JSONObject iv = (JSONObject) v.get(IDENTITY_VERIFICATION);
		String similarity = iv.get(SIMILARITY).toString();
		similarity = similarity.replaceAll("\"", "");
		if (similarity.equals(MATCH)) {
			result.put(OUTCOME, PASSED);
		}
		if (similarity.equals(NO_MATCH)) {
			result.put(OUTCOME, REJECTED);
		}
		
		*/


		return result;
	}

	private JSONObject getRetrievalMessage(String serverURL) throws Exception {
		
		String accessToken = JumioUtils.getAccessToken(serviceConfig);

		URL url = new URL(serverURL);

		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setDoOutput(true);
		conn.setDoInput(true);
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Accept", "application/json");
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("User-Agent", "Jumio ForgeRock/1.1.2");
		conn.setRequestProperty("Authorization", "Bearer " + accessToken);
		JSONObject retVal = new JSONObject(JumioUtils.convertStreamToString(conn.getInputStream()));
		conn.disconnect();
		
		return retVal;
	}

	@Override
	public Action process(TreeContext context) {
		try {
			JsonValue sharedState = context.sharedState;
			String wfExID = sharedState.get(WORKFLOW_EXECUTION_ID).asString();
			String acctID = sharedState.get(ACCOUNT_ID).asString();

			String jumioStatus = checkStatus(wfExID, acctID);
			if (StringUtils.equalsIgnoreCase(jumioStatus, INITIATED) || StringUtils.equalsIgnoreCase(jumioStatus, ACQUIRED)) {
				logger.info(loggerPrefix + "Id verification still pending.");
				return Action.goTo(PENDING_OUTCOME).build();
			} else if (StringUtils.equalsIgnoreCase(jumioStatus, PROCESSED)) {
				logger.info(loggerPrefix + "Id verification complete.  Proceeding");
				JSONObject results = getVerificationResults(wfExID, acctID);

				if (logger.isInfoEnabled()) {
					logger.info(loggerPrefix + "Workflow Execution ID: " + wfExID + " Status: " + jumioStatus
							+ " outcome: " + results.getString(OUTCOME));
				}

				// TODO: Call cfgAccountMapperConfiguration to get which Jumio attributes the
				// customer wants to map to FR
				// attributes
				String outcome =  results.getJSONObject(DECISION).getString(TYPE);
				switch (outcome) {
				case PASSED:
					sharedState.put("RESULTS", results.toString());
					Map<String, String> map = config.cfgAccountMapperConfiguration();
					try {
						JsonValue attributes = json(object(map.size() + 1));
						String username = sharedState.get(USERNAME).asString();
						List<Object> uidArray = array();
						uidArray.add(username);
						attributes.put(UID, uidArray);

						for (Map.Entry<String, String> entry : map.entrySet()) {
							attributes.put(entry.getValue(), array(results.getString(entry.getKey())));
						}
						JsonValue userInfo = json(object());
						userInfo.put(ATTRIBUTES, attributes);
						JsonValue userNames = json(object(1));
						List<Object> usernameArray = array();
						usernameArray.add(username);

						userNames.put(USERNAME, usernameArray);
						userInfo.put(USER_NAMES, userNames);

						sharedState.put(USER_INFO, userInfo);

					} catch (JSONException je) {
						if (logger.isInfoEnabled()) {
							logger.info(loggerPrefix + je.getMessage());
						}
						throw new NodeProcessException(je);
					}

					return Action.goTo(PASSED).replaceSharedState(sharedState).build();
				case REJECTED:
					return Action.goTo(REJECTED).build();
				case WARNING:
					sharedState.put("RESULTS", results.toString());
					Map<String, Object> resultMap = results.toMap();
					for (Iterator<String> it = resultMap.keySet().iterator(); it.hasNext();) {
						String thisKey = it.next();
						System.out.println("TESTTESTTEST:   HERE this MAP Entry= " + thisKey + " : " + resultMap.get(thisKey));
					}
					Map<String, String> map2 = config.cfgAccountMapperConfiguration();
					try {
						JsonValue attributes = json(object(map2.size() + 1));
						String username = sharedState.get(USERNAME).asString();
						List<Object> uidArray = array();
						uidArray.add(username);
						attributes.put(UID, uidArray);

						for (Map.Entry<String, String> entry : map2.entrySet()) {
							attributes.put(entry.getValue(), array(results.getString(entry.getKey())));
						}
						JsonValue userInfo = json(object());
						userInfo.put(ATTRIBUTES, attributes);
						JsonValue userNames = json(object(1));
						List<Object> usernameArray = array();
						usernameArray.add(username);

						userNames.put(USERNAME, usernameArray);
						userInfo.put(USER_NAMES, userNames);

						sharedState.put(USER_INFO, userInfo);

					} catch (JSONException je) {
						if (logger.isInfoEnabled()) {
							logger.info(loggerPrefix + je.getMessage());
						}
						throw new NodeProcessException(je);
					}
					return Action.goTo(WARNING).replaceSharedState(sharedState).build();
				default:
					return Action.goTo(NOT_EXECUTED).replaceSharedState(sharedState).build();
				}
			} else {
				context.getStateFor(this).putShared(loggerPrefix + "Error Status from Jumio", new Date() + ": " + jumioStatus);
				return Action.goTo(JumioConstants.ERROR_OUTCOME).build();
			}

		} catch (Exception ex) {
			logger.error(loggerPrefix + "Exception occurred: " + ex.getMessage());
			logger.error(loggerPrefix + "Exception occurred: " + ex.getStackTrace());
			ex.printStackTrace();
			context.getStateFor(this).putShared(loggerPrefix + "Exception", new Date() + ": " + ex.getMessage());
			return Action.goTo(JumioConstants.ERROR_OUTCOME).build();
		}

	}

	/**
	 * Defines the possible outcomes from this node.
	 */
	public static class JumioDecisionOutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
		@Override
		public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
			return new ArrayList<Outcome>() {
				{
					add(new Outcome(PASSED, PASSED));
					add(new Outcome(NOT_EXECUTED, NOT_EXECUTED));
					add(new Outcome(REJECTED, REJECTED));
					add(new Outcome(WARNING, WARNING));
					add(new Outcome(PENDING_OUTCOME, PENDING_OUTCOME));
					add(new Outcome(ERROR_OUTCOME, ERROR_OUTCOME));
				}
			};
		}
	}

}