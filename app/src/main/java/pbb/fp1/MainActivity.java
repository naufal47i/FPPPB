package pbb.fp1;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    Button scanButton;  // Renamed variable for clarity
    Button searchButton; // Renamed variable for clarity
    static final int IMAGE_CAPTURE_REQUEST = 1;  // Renamed constant for clarity
    static final int IMAGE_PICK_REQUEST = 2;  // Renamed constant for clarity
    Uri imageUri;

    // ActivityResultLauncher for capturing an image
    private final ActivityResultLauncher<Intent> imageCaptureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    processCapturedImage(result.getData());
                }
            });

    // ActivityResultLauncher for selecting an image from the gallery
    private final ActivityResultLauncher<Intent> imagePickLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    processPickedImage(result.getData().getData());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check permissions before proceeding with image capture or selection
        requestPermissions();

        // Initialize buttons
        scanButton = findViewById(R.id.scan);
        searchButton = findViewById(R.id.search);

        // Set click listeners for buttons
        scanButton.setOnClickListener(view -> {
            Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            imageCaptureLauncher.launch(captureIntent);  // Launch the camera to capture an image
        });

        searchButton.setOnClickListener(view -> {
            Intent pickIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickLauncher.launch(pickIntent);  // Launch the gallery to select an image
        });
    }

    // Check and request permissions for reading external storage
    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1001);  // Request permission
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Handle result from capturing an image
        if (requestCode == IMAGE_CAPTURE_REQUEST && resultCode == RESULT_OK) {
            processCapturedImage(data);
        }
        // Handle result from selecting an image from gallery
        else if (requestCode == IMAGE_PICK_REQUEST && resultCode == RESULT_OK) {
            processPickedImage(data.getData());
        }
    }

    // Process the captured image from the camera
    private void processCapturedImage(Intent data) {
        Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");  // Extract the image

        File imageFile = new File(getExternalFilesDir(null), "captured_image.jpg");
        try (FileOutputStream out = new FileOutputStream(imageFile)) {
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);  // Compress the image to a file
            imageUri = Uri.fromFile(imageFile);  // Get URI for the image file

            Result.getUri(imageUri.toString());  // Send URI to Result class
            Toast.makeText(this, "Image Saved", Toast.LENGTH_SHORT).show();

            // Perform local inference with the captured image
            new InferenceLocal(this, imageFile.getAbsolutePath()).execute();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving image", Toast.LENGTH_SHORT).show();
        }
    }

    // Process the selected image from the gallery
    private void processPickedImage(Uri uri) {
        if (uri == null) {
            Toast.makeText(this, "Invalid image URI", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Decode the image to bitmap
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
            File imageFile = new File(getExternalFilesDir(null), "selected_image.jpg");

            try (FileOutputStream out = new FileOutputStream(imageFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);  // Compress the image to a file
                imageUri = Uri.fromFile(imageFile);  // Get URI for the image file

                Result.getUri(imageUri.toString());  // Send URI to Result class
                Toast.makeText(this, "Image Saved", Toast.LENGTH_SHORT).show();

                // Perform local inference with the selected image
                new InferenceLocal(this, imageFile.getAbsolutePath()).execute();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error processing selected image", Toast.LENGTH_SHORT).show();
        }
    }

    // Handle the result of permission request
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

class InferenceLocal extends AsyncTask<Void, Void, String> {

    private String imagePath;
    private Context context;

    public InferenceLocal(Context context, String imagePath) {
        this.context = context;
        this.imagePath = imagePath;
    }

    @Override
    protected String doInBackground(Void... voids) {
        String API_KEY = "fzQ02RGXpfIxgn0KlATn"; // Your API Key
        String MODEL_ENDPOINT = "oral-cancer-up2pr/3"; // model endpoint

        // File input stream for the image
        File imageFile = new File(imagePath);

        // Construct the URL for API endpoint
        String uploadURL = "https://detect.roboflow.com/" + MODEL_ENDPOINT + "?api_key=" + API_KEY;

        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;
        BufferedReader reader = null;
        String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
        try {
            // Open a URL connection
            URL url = new URL(uploadURL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setDoOutput(true);

            outputStream = new DataOutputStream(connection.getOutputStream());

            // Send image as multipart data
            sendFormData(outputStream, boundary, imageFile);

            // Get response from the server
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return response.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.flush();
                    outputStream.close();
                }
                if (reader != null) {
                    reader.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Send image data as multipart form data to the API
    private void sendFormData(DataOutputStream outputStream, String boundary, File imageFile) throws IOException {
        String fileName = imageFile.getName();
        String fileMimeType = "image/jpeg"; // Assuming the image is JPEG; you can adjust this based on your file type

        // Send the form data header
        outputStream.writeBytes("--" + boundary + "\r\n");
        outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
        outputStream.writeBytes("Content-Type: " + fileMimeType + "\r\n");
        outputStream.writeBytes("\r\n");

        // Write the file content to the output stream
        FileInputStream fileInputStream = new FileInputStream(imageFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        fileInputStream.close();

        // End the multipart form data
        outputStream.writeBytes("\r\n");
        outputStream.writeBytes("--" + boundary + "--\r\n");
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        // Handle the result from the API (for example, update the UI)
        if (result != null) {
            try {
                JSONObject jsonObject = new JSONObject(result);
                JSONArray predictionsArray = jsonObject.getJSONArray("predictions");

                if (predictionsArray.length() > 0) {
                    String className = predictionsArray.getJSONObject(0).getString("class");
                    System.out.println("Class: " + className);

                    double confidence = predictionsArray.getJSONObject(0).getDouble("confidence");
                    System.out.println("Confidence: " + confidence);

                    Result.getResult(className, confidence);
                } else {
                    Toast.makeText(context, "No predictions found", Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } finally {
                System.out.println("API Response: " + result);
                Intent intent = new Intent(context, Result.class);
                context.startActivity(intent);
            }
        } else {
            System.out.println("Error: No response from API");
            Toast.makeText(context, "Error: No response from API", Toast.LENGTH_SHORT).show();
        }
    }
}
