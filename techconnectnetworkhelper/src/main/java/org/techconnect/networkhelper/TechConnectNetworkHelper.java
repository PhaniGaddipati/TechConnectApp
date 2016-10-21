package org.techconnect.networkhelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.techconnect.networkhelper.model.ChartComment;
import org.techconnect.networkhelper.model.FlowChart;
import org.techconnect.networkhelper.model.JsendResponse;
import org.techconnect.networkhelper.model.Tokens;
import org.techconnect.networkhelper.model.Vertex;
import org.techconnect.networkhelper.serializers.FlowChartDeserializer;
import org.techconnect.networkhelper.serializers.FlowChartSerializer;
import org.techconnect.networkhelper.serializers.JsendResponseDeserializer;
import org.techconnect.networkhelper.serializers.VertexDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TechConnectNetworkHelper {

    public static final String BASE_URL = "http://jhtechconnect.me/";
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private Tokens user = new Tokens();
    private Gson myGson = buildGson();
    private Retrofit retrofit = new Retrofit.Builder().baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(myGson))

            .build();
    private org.techconnect.networkhelper.TechConnectRetrofit service = retrofit.create(org.techconnect.networkhelper.TechConnectRetrofit.class);

    //Store references to the possible device, problem, and misc flowcharts
    private ArrayList<String> device_ids = new ArrayList<String>();
    private ArrayList<String> problem_ids = new ArrayList<String>();
    private ArrayList<String> misc_ids = new ArrayList<String>();

    private static Gson buildGson() {
        GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(FlowChart.class, new FlowChartDeserializer());
        gsonBuilder.registerTypeAdapter(FlowChart.class, new FlowChartSerializer());
        gsonBuilder.registerTypeAdapter(JsendResponse.class, new JsendResponseDeserializer());
        gsonBuilder.registerTypeAdapter(Vertex.class, new VertexDeserializer());
        Gson myGson = gsonBuilder.create();

        return myGson;
    }

    /**
     * This method uses the device_ids object to download all device-related flowcharts
     * Does NOT download the associated files. This is up to the IntentService which is doing the work
     *
     * @return
     */
    public List<FlowChart> getDevices() {
        ArrayList<FlowChart> devices = new ArrayList<FlowChart>();
        String[] ids = new String[device_ids.size()];
        ids = device_ids.toArray(ids);
        try {
            devices = (ArrayList<FlowChart>) getCharts(ids);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return devices;
    }

    /**
     * This method uses the problem_ids object to download all problem-related charts
     *
     * @return
     */
    public List<FlowChart> getProblems() {
        ArrayList<FlowChart> probs = new ArrayList<FlowChart>();
        String[] ids = new String[problem_ids.size()];
        ids = problem_ids.toArray(ids);
        try {
            probs = (ArrayList<FlowChart>) getCharts(ids);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return probs;
    }

    /**
     * This method uses the Misc_ids object to download all misc-related charts
     *
     * @return
     */
    public List<FlowChart> getMisc() {
        ArrayList<FlowChart> misc = new ArrayList<FlowChart>();
        String[] ids = new String[misc_ids.size()];
        ids = misc_ids.toArray(ids);
        try {
            misc = (ArrayList<FlowChart>) getCharts(ids);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return misc;
    }

    /**
     * This function retrieves the getCatalog of devices from the server.
     *
     * @param update_lists - This is a boolean used to determine whether we want to update the current
     *                     list of devices recognized by the helper.
     * @return
     * @throws IOException
     */
    public List<FlowChart> getCatalog(boolean update_lists) throws IOException {
        //Call and get a response
        Response<JsendResponse> resp = service.getCatalog().execute();
        //First, check whether there is an error. HAVE TO DO THIS TO SATISFY RETROFIT!
        //Cheking first if the http request was successful. if not, have to manually deserialize the JSON
        if (!resp.isSuccessful()) {
            JsendResponse error = myGson.fromJson(resp.errorBody().string(), JsendResponse.class);
            throw new IOException(error.getMessage());
        } else {
            //Now, I'm expecting a getCatalog
            JsonObject obj = resp.body().getData();
            if (update_lists) {
                this.device_ids.clear();
                this.problem_ids.clear();
                this.misc_ids.clear();
            }
            //Obj should have a JsonArray of flowcharts
            ArrayList<FlowChart> flowcharts = new ArrayList<FlowChart>();
            for (JsonElement j : obj.get("flowcharts").getAsJsonArray()) {
                FlowChart f = myGson.fromJson(j, FlowChart.class);
                //Read through each flowchart and identify the the devices, problems, and misc
                //Only if requested
                if (update_lists) {
                    switch (f.getType()) {
                        case DEVICE:
                            this.device_ids.add(f.getId());
                            break;
                        case PROBLEM:
                            this.problem_ids.add(f.getId());
                            break;
                        default:
                            //For now, until we have a reason otherwise!!!!
                            //!!!!!
                            this.device_ids.add(f.getId());
                    }

                }
                flowcharts.add(f);
            }
            return flowcharts;
        }
    }

    public FlowChart getChart(String id) throws IOException {
        Response<JsendResponse> resp = service.getFlowchart(id).execute();
        //First, check whether there is an error
        if (!resp.isSuccessful()) {
            JsendResponse error = myGson.fromJson(resp.errorBody().string(), JsendResponse.class);
            throw new IOException(error.getMessage());
        } else {
            //Now, I know that there is a FlowChart contained in this resp. Just get it
            JsonObject obj = resp.body().getData();
            return myGson.fromJson(obj.get("flowchart"), FlowChart.class);
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
    public List<FlowChart> getCharts(String[] ids) throws IOException {
        Response<JsendResponse> resp = service.getFlowcharts(ids).execute();
        if (!resp.isSuccessful()) {
            JsendResponse error = myGson.fromJson(resp.errorBody().string(), JsendResponse.class);
            throw new IOException(error.getMessage());
        } else {
            //Now I know, I should be getting two objects. bad ID strings as well as the actual flowcharts
            JsonObject obj = resp.body().getData();
            //For now, let's just look at the good charts
            ArrayList<FlowChart> flowcharts = new ArrayList<FlowChart>();
            for (JsonElement j : obj.get("flowcharts").getAsJsonArray()) {
                flowcharts.add(myGson.fromJson(j, FlowChart.class));
            }
            return flowcharts;
        }
    }

    //
    //I have no clue how this should be done at all just threw something together.
    public void login(String email, String password) throws IOException {
        Response<JsendResponse> resp = service.login(email, password).execute();
        //System.out.println(service.login(email,password).execute().code());
        //First check to see if the request succeeded
        if (!resp.isSuccessful()) {
            //Must convert ourselves
            JsendResponse test = myGson.fromJson(resp.errorBody().string(), JsendResponse.class);
            throw new IOException(test.getMessage());
        } else {
            //Now, I'm expecting a data object with fields relevant
            JsonObject obj = resp.body().getData().getAsJsonObject();
            user = myGson.fromJson(obj, Tokens.class);
        }
    }

    public void logout() throws IOException {
        Response<JsendResponse> resp = service.logout(user.getAuthToken(), user.getUserId()).execute();
        if (!resp.isSuccessful()) {
            JsendResponse test = myGson.fromJson(resp.errorBody().string(), JsendResponse.class);
            throw new IOException(test.getMessage());
        } else {
            user = null;//Resest the user object so that it doesn't bleed over into future calls
        }


    }

    /**
     * Use this method to comment on a flowchart
     *
     * @param chart_id - ID of the flowchart to post to
     * @param c        - The actual comment object
     * @throws IOException
     */
    public void comment(String chart_id, ChartComment c) throws IOException {
        Response<JsendResponse> resp = service.postComment(user.getAuthToken(), user.getUserId(), chart_id, c).execute();
        if (!resp.isSuccessful()) {
            JsendResponse error = myGson.fromJson(resp.errorBody().string(), JsendResponse.class);
            throw new IOException(error.getMessage());
        }
    }

    /**
     * Use this method to delete a comment on a flowchart on the server
     *
     * @param chart_id   - ID of the chart
     * @param comment_id - ID of the
     */
    public void deleteComment(String chart_id, String comment_id) throws IOException {
        //First, I need to build the RequestBody object to handle the single string
        JsonObject body = new JsonObject();
        body.addProperty("commentId", comment_id);
        RequestBody requestBody = RequestBody.create(JSON, body.toString());

        Response<JsendResponse> resp = service.deleteComment(user.getAuthToken(), user.getUserId(), chart_id, requestBody).execute();
        if (!resp.isSuccessful()) {
            JsendResponse error = myGson.fromJson(resp.errorBody().string(), JsendResponse.class);
            throw new IOException(error.getMessage());
        }

    }

    /**
     * Use this method to provide feedback on the chart (up-vote, down-vote currently)
     *
     * @param upVote - Boolean which determines whether up-vote (true)
     */
    public void postFeedback(String chart_id, boolean upVote) throws IOException {
        String vote = upVote ? "true" : "false";
        JsonObject body = new JsonObject();
        body.addProperty("feedback", vote);
        RequestBody requestBody = RequestBody.create(JSON, body.toString());

        Response<JsendResponse> resp = service.postFeedback(user.getAuthToken(), user.getUserId(), chart_id, requestBody).execute();
        if (!resp.isSuccessful()) {
            JsendResponse error = myGson.fromJson(resp.errorBody().string(), JsendResponse.class);
            throw new IOException(error.getMessage());
        }
    }


}