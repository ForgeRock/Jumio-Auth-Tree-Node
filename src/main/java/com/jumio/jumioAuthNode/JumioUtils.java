package com.jumio.jumioAuthNode;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.json.JSONObject;

class JumioUtils {

	private JumioUtils() {
	}

	static String convertStreamToString(InputStream is) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder retVal = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			retVal.append(line).append("\n");
		}

		reader.close();
		return retVal.toString();
	}

	static String getAccessToken(JumioService serviceConfig) throws Exception {
		String retVal = null;

		String auth = serviceConfig.token() + ":" + String.valueOf(serviceConfig.secret());
		auth = Base64.getEncoder().encodeToString(auth.getBytes());
		HttpURLConnection conn_auth = null;
		OutputStreamWriter wr_auth = null;
		try {
			URL url_auth;

			url_auth = new URL("https://auth." + serviceConfig.serverUrl().toString()
					+ "/oauth2/token?grant_type=client_credentials");

			conn_auth = (HttpURLConnection) url_auth.openConnection();
			conn_auth.setDoOutput(true);
			conn_auth.setDoInput(true);
			conn_auth.setRequestMethod("POST");
			conn_auth.setRequestProperty("Accept", "application/json");
			conn_auth.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

			conn_auth.setRequestProperty("Authorization", "Basic " + auth);

			String streamToString_auth;
			int responseCode_auth;

			wr_auth = new OutputStreamWriter(conn_auth.getOutputStream());

			wr_auth.flush();
			streamToString_auth = JumioUtils.convertStreamToString(conn_auth.getInputStream());
			responseCode_auth = conn_auth.getResponseCode();

			if (responseCode_auth == 200) {
				JSONObject jo_auth = new JSONObject(streamToString_auth);
				retVal = jo_auth.getString("access_token");
			} else {
				throw new NodeProcessException("OAuth Access token could not be retrieved");
			}

		} catch (Exception e) {
			throw new Exception(e);
		} finally {
			try {
				if (wr_auth != null)
					wr_auth.close();
				if (conn_auth != null)
					conn_auth.disconnect();
			} catch (Exception e) {
				// Do nothing
			}
		}

		return retVal;
	}

}