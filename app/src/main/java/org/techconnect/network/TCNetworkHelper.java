package org.techconnect.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.techconnect.model.Comment;
import org.techconnect.model.FlowChart;
import org.techconnect.model.JsendResponse;
import org.techconnect.model.UserAuth;
import org.techconnect.model.Vertex;
import org.techconnect.network.serializers.FlowChartDeserializer;
import org.techconnect.network.serializers.FlowChartSerializer;
import org.techconnect.network.serializers.JsendResponseDeserializer;
import org.techconnect.network.serializers.VertexDeserializer;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TCNetworkHelper {

    public static final String BASE_URL = "https://jhtechconnect.me/";
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private Gson gson;
    private TCRetrofit service;
    private JsendResponse lastError = null;

    public TCNetworkHelper() {
        gson = buildGson();
        service = new Retrofit.Builder().baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build().create(TCRetrofit.class);
    }

    private Gson buildGson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(FlowChart.class, new FlowChartDeserializer());
        gsonBuilder.registerTypeAdapter(FlowChart.class, new FlowChartSerializer());
        gsonBuilder.registerTypeAdapter(JsendResponse.class, new JsendResponseDeserializer());
        gsonBuilder.registerTypeAdapter(Vertex.class, new VertexDeserializer());
        return gsonBuilder.create();
    }

    /**
     * This function retrieves the getCatalog of devices from the server.
     *
     * @return All flowcharts in the catalog. These flowcharts do not have the graph loaded.
     * @throws IOException
     */
    public FlowChart[] getCatalog() throws IOException {
        Response<JsendResponse> resp = service.getCatalog().execute();
        //First, check whether there is an error. HAVE TO DO THIS TO SATISFY RETROFIT!
        //Cheking first if the http request was successful. if not, have to manually deserialize the JSON
        if (!resp.isSuccessful()) {
            JsendResponse error = gson.fromJson(resp.errorBody().string(), JsendResponse.class);
            throw new IOException(error.getMessage());
        } else {
            //Now, I'm expecting a getCatalog
            JsonObject obj = resp.body().getData();
            //Obj should have a JsonArray of flowcharts
            ArrayList<FlowChart> flowcharts = new ArrayList<FlowChart>();
            for (JsonElement j : obj.get("flowcharts").getAsJsonArray()) {
                FlowChart f = gson.fromJson(j, FlowChart.class);
                flowcharts.add(f);
            }
            return flowcharts.toArray(new FlowChart[flowcharts.size()]);
        }
    }

    public FlowChart getChart(String id) throws IOException {
        Response<JsendResponse> resp = service.getFlowchart(id).execute();
        //First, check whether there is an error
        if (!resp.isSuccessful()) {
            JsendResponse error = gson.fromJson(resp.errorBody().string(), JsendResponse.class);
            throw new IOException(error.getMessage());
        } else {
            //Now, I know that there is a FlowChart contained in this resp. Just get it
            JsonObject obj = resp.body().getData();
            return gson.fromJson(obj.get("flowchart"), FlowChart.class);
        }
    }

    /**
     * This function is used to get a list of specific charts. This list must be sent to
     * Retrofit as a comma separated list of ids as a String. This converts from a list of integers
     * for now. May not want that format in the future, but it's a place to start.
     *
     * @param ids
     * @return
     * @throws IOException
     */
    public FlowChart[] getCharts(String[] ids) throws IOException {
        Response<JsendResponse> resp = service.getFlowcharts(ids).execute();
        if (!resp.isSuccessful()) {
            JsendResponse error = gson.fromJson(resp.errorBody().string(), JsendResponse.class);
            throw new IOException(error.getMessage());
        } else {
            //Now I know, I should be getting two objects. bad ID strings as well as the actual flowcharts
            JsonObject obj = resp.body().getData();
            //For now, let's just look at the good charts
            ArrayList<FlowChart> flowcharts = new ArrayList<FlowChart>();
            for (JsonElement j : obj.get("flowcharts").getAsJsonArray()) {
                flowcharts.add(gson.fromJson(j, FlowChart.class));
            }
            return flowcharts.toArray(new FlowChart[flowcharts.size()]);
        }
    }

    public UserAuth login(String email, String password) throws IOException {
        Response<JsendResponse> resp = service.login(email, password).execute();
        //First check to see if the request succeeded
        if (!resp.isSuccessful()) {
            JsendResponse test = gson.fromJson(resp.errorBody().string(), JsendResponse.class);
            return null;
        } else {
            //Now, I'm expecting a data object with fields relevant
            JsonObject obj = resp.body().getData().getAsJsonObject();
            return gson.fromJson(obj, UserAuth.class);
        }
    }

    public boolean logout(UserAuth auth) throws IOException {
        Response<JsendResponse> resp = service.logout(auth.getAuthToken(), auth.getUserId()).execute();
        if (!resp.isSuccessful()) {
            JsendResponse test = gson.fromJson(resp.errorBody().string(), JsendResponse.class);
            return false;
        }
        return true;
    }

    /**
     * Use this method to comment on a flowchart
     *
     * @param chart_id - ID of the flowchart to post to
     * @param c        - The actual comment object
     * @throws IOException
     */
    public FlowChart comment(String chart_id, Comment c, UserAuth auth) throws IOException {
        Response<JsendResponse> resp = service.postComment(auth.getAuthToken(), auth.getUserId(), chart_id, c).execute();
        if (!resp.isSuccessful()) {
            lastError = gson.fromJson(resp.errorBody().string(), JsendResponse.class);
            return null;
        }
        return gson.fromJson(resp.body().getData().get("flowchart"), FlowChart.class);
    }

    /**
     * Use this method to delete a comment on a flowchart on the server
     *
     * @param chart_id   - ID of the chart
     * @param comment_id - ID of the
     */
    public FlowChart deleteComment(String chart_id, String comment_id, UserAuth auth) throws IOException {
        //First, I need to build the RequestBody object to handle the single string
        JsonObject body = new JsonObject();
        body.addProperty("commentId", comment_id);
        RequestBody requestBody = RequestBody.create(JSON, body.toString());
        Response<JsendResponse> resp = service.deleteComment(auth.getAuthToken(), auth.getUserId(),
                chart_id, requestBody).execute();
        if (!resp.isSuccessful()) {
            lastError = gson.fromJson(resp.errorBody().string(), JsendResponse.class);
            return null;
        }
        return gson.fromJson(resp.body().getData().get("flowchart"), FlowChart.class);

    }

    /**
     * Use this method to provide feedback on the chart (up-vote, down-vote currently)
     *
     * @param upVote - Boolean which determines whether up-vote (true)
     */
    public FlowChart postFeedback(String chart_id, boolean upVote, UserAuth auth) throws IOException {
        String vote = upVote ? "true" : "false";
        JsonObject body = new JsonObject();
        body.addProperty("feedback", vote);
        RequestBody requestBody = RequestBody.create(JSON, body.toString());

        Response<JsendResponse> resp = service.postFeedback(auth.getAuthToken(),
                auth.getUserId(), chart_id, requestBody).execute();
        if (!resp.isSuccessful()) {
            lastError = gson.fromJson(resp.errorBody().string(), JsendResponse.class);
            return null;
        }
        return gson.fromJson(resp.body().getData().get("flowchart"), FlowChart.class);
    }

    public JsendResponse getLastError() {
        return lastError;
    }
}