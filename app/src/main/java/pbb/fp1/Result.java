package pbb.fp1;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class Result extends AppCompatActivity {

    // Define UI components for displaying results
    TextView tvpercentage;
    TextView tvClassResult;
    ImageView photo;

    // Static variables to store the class name, confidence (percentage), and image URI
    static double percentageResult; // Confidence value (percentage)
    static String classNameResult;
    static String uriResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.resulttest);

        // Initialize the UI components
        tvpercentage = findViewById(R.id.rpercentage);
        tvClassResult = findViewById(R.id.classresult);  // Initialize tvClassResult here
        photo = findViewById(R.id.photo);

        // Load and display the image if the URI is available
        if (uriResult != null) {
            Uri imageUri = Uri.parse(uriResult);  // Convert the stored URI string to a Uri object
            Bitmap bitmap = BitmapFactory.decodeFile(imageUri.getPath());  // Decode the image from the file path
            photo.setImageBitmap(bitmap);  // Set the decoded image to the ImageView
        }

        // Display the class name and confidence percentage if available
        if (classNameResult != null && percentageResult != 0) {
            String formattedResult = String.format("%.2f%%", percentageResult * 100);  // Format the confidence percentage
            tvpercentage.setText(formattedResult);  // Display the confidence percentage
            tvClassResult.setText(classNameResult);  // Display the class name
        } else {
            tvpercentage.setText("Not Detected");  // Show "Not Detected" if no result is available
            tvClassResult.setText("");  // Clear the class name if no result
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        resetResult();  // Reset the result data when the activity is destroyed
    }

    // Modify getResult to accept both className and confidence as parameters
    public static void getResult(String className, double confidence) {
        classNameResult = className;  // Set the class name
        percentageResult = confidence;  // Set the confidence value
    }

    // Static method to reset the stored result values
    public static void resetResult() {
        classNameResult = null; // Reset class name to null
        percentageResult = 0;   // Reset confidence to 0
        uriResult = null;       // Reset URI to null
    }

    // Method to set the image URI
    public static void getUri(String uri) {
        uriResult = uri;  // Set the image URI
    }
}
