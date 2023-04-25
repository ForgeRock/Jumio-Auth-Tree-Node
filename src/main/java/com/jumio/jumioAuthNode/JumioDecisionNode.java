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
import static com.jumio.jumioAuthNode.JumioConstants.ACQUIRED;
import static com.jumio.jumioAuthNode.JumioConstants.ATTRIBUTES;
import static com.jumio.jumioAuthNode.JumioConstants.DECISION;
import static com.jumio.jumioAuthNode.JumioConstants.ERROR_OUTCOME;
import static com.jumio.jumioAuthNode.JumioConstants.INITIATED;
import static com.jumio.jumioAuthNode.JumioConstants.NOT_EXECUTED;
import static com.jumio.jumioAuthNode.JumioConstants.PASSED;
import static com.jumio.jumioAuthNode.JumioConstants.PENDING_OUTCOME;
import static com.jumio.jumioAuthNode.JumioConstants.PROCESSED;
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
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.forgerock.util.i18n.PreferredLocales;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.sm.SMSException;

@Node.Metadata(outcomeProvider = JumioDecisionNode.JumioDecisionOutcomeProvider.class, configClass = JumioDecisionNode.Config.class, tags = {
		"marketplace", "trustnetwork" })
public class JumioDecisionNode extends AbstractDecisionNode {

	private final Logger logger = LoggerFactory.getLogger(JumioDecisionNode.class);
	private final JumioService serviceConfig;
	private final Config config;
	private String loggerPrefix = "[Jumio Decision][Marketplace] ";
	private static final String BUNDLE = JumioDecisionNode.class.getName();

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
		return result;
	}

	private JSONObject getRetrievalMessage(String serverURL) throws Exception {

		String accessToken = JumioUtils.getAccessToken(serviceConfig);

		URL url = new URL(serverURL);
		JSONObject retVal = null;

		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("User-Agent", "Jumio ForgeRock/1.1.2");
			conn.setRequestProperty("Authorization", "Bearer " + accessToken);
			retVal = new JSONObject(JumioUtils.convertStreamToString(conn.getInputStream()));
		} catch (Exception e) {
			throw new Exception(e);
		} finally {
			try {

				if (conn != null) {
					conn.disconnect();
				}
			} catch (Exception e) {
				// Do nothing
			}

		}

		return retVal;
	}

	@Override
	public Action process(TreeContext context) {
		try {
			NodeState ns = context.getStateFor(this);
			
			String wfExID = ns.get(WORKFLOW_EXECUTION_ID).asString();
			String acctID = ns.get(ACCOUNT_ID).asString();

			String jumioStatus = checkStatus(wfExID, acctID);
			if (StringUtils.equalsIgnoreCase(jumioStatus, INITIATED) || StringUtils.equalsIgnoreCase(jumioStatus, ACQUIRED)) {
				logger.info(loggerPrefix + "Id verification still pending.");
				return Action.goTo(PENDING_OUTCOME).build();
			} else if (StringUtils.equalsIgnoreCase(jumioStatus, PROCESSED)) {
				logger.info(loggerPrefix + "Id verification complete.  Proceeding");
				JSONObject results = getVerificationResults(wfExID, acctID);


				// TODO: Call cfgAccountMapperConfiguration to get which Jumio attributes the
				// customer wants to map to FR
				// attributes
				String outcome =  results.getJSONObject(DECISION).getString(TYPE);
				switch (outcome) {
				case PASSED:
					ns.putShared("RESULTS", results.toString());
					JSONArray extractionJSON = results.getJSONObject("capabilities").getJSONArray("extraction");
					if (extractionJSON!=null && extractionJSON.get(0)!=null && ((JSONObject)extractionJSON.get(0)).getJSONObject("data")!=null) {
						Map<String, Object> flattenedJsonMap = ((JSONObject)extractionJSON.get(0)).getJSONObject("data").toMap();						
						Map<String, String> map2 = config.cfgAccountMapperConfiguration();
						try {
							JsonValue attributes = json(object(map2.size() + 1));
							String username = ns.get(USERNAME).asString();
							List<Object> uidArray = array();
							uidArray.add(username);
							attributes.put(UID, uidArray);

							for (Map.Entry<String, String> entry : map2.entrySet()) {
								attributes.put(entry.getValue(), array(flattenedJsonMap.get(entry.getKey())));
							}
							JsonValue userInfo = json(object());
							userInfo.put(ATTRIBUTES, attributes);
							JsonValue userNames = json(object(1));
							List<Object> usernameArray = array();
							usernameArray.add(username);

							userNames.put(USERNAME, usernameArray);
							userInfo.put(USER_NAMES, userNames);

							ns.putShared(USER_INFO, userInfo);

						} catch (JSONException je) {
							if (logger.isInfoEnabled()) {
								logger.info(loggerPrefix + je.getMessage());
							}
							throw new NodeProcessException(je);
						}
					}
					return Action.goTo(PASSED).build();
				case REJECTED:
					return Action.goTo(REJECTED).build();
				case WARNING:
					ns.putShared("RESULTS", results.toString());
					JSONArray extractionJSON2 = results.getJSONObject("capabilities").getJSONArray("extraction");
					if (extractionJSON2!=null && extractionJSON2.get(0)!=null && ((JSONObject)extractionJSON2.get(0)).getJSONObject("data")!=null) {
						Map<String, Object> flattenedJsonMap = ((JSONObject)extractionJSON2.get(0)).getJSONObject("data").toMap();						
						Map<String, String> map2 = config.cfgAccountMapperConfiguration();
						try {
							JsonValue attributes = json(object(map2.size() + 1));
							String username = ns.get(USERNAME).asString();
							List<Object> uidArray = array();
							uidArray.add(username);
							attributes.put(UID, uidArray);

							for (Map.Entry<String, String> entry : map2.entrySet()) {
								attributes.put(entry.getValue(), array(flattenedJsonMap.get(entry.getKey())));
							}
							JsonValue userInfo = json(object());
							userInfo.put(ATTRIBUTES, attributes);
							JsonValue userNames = json(object(1));
							List<Object> usernameArray = array();
							usernameArray.add(username);

							userNames.put(USERNAME, usernameArray);
							userInfo.put(USER_NAMES, userNames);

							ns.putShared(USER_INFO, userInfo);

						} catch (JSONException je) {
							if (logger.isInfoEnabled()) {
								logger.info(loggerPrefix + je.getMessage());
							}
							throw new NodeProcessException(je);
						}
					}
					return Action.goTo(WARNING).build();
				default:
					return Action.goTo(NOT_EXECUTED).build();
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
			ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE,
					JumioDecisionOutcomeProvider.class.getClassLoader());
			return ImmutableList.of(
				new Outcome(PASSED,  bundle.getString("PassedOutcome")),
				new Outcome(NOT_EXECUTED,  bundle.getString("NotExecutedOutcome")),
				new Outcome(REJECTED,  bundle.getString("RejectedOutcome")),
				new Outcome(WARNING,  bundle.getString("WarningOutcome")),
				new Outcome(PENDING_OUTCOME,  bundle.getString("PendingOutcome")),
				new Outcome(ERROR_OUTCOME,  bundle.getString("ErrorOutcome")));
		}
	}

}
