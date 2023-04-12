package com.example.loseit;

import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.loseit.databinding.ActivityCreateForumRecipeBinding;
import com.example.loseit.model.DietItem;
import com.example.loseit.model.RecipeItem;
import com.example.loseit.ui.diet.DietItemDialog;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CreateForumRecipeActivity extends AppCompatActivity {
    ActivityCreateForumRecipeBinding binding;
    public static final String DB_FORUM_RECIPE_PATH = "recipes";
    public static final String DB_FORUM_RECIPE_IMG_PATH = "recipe_images";
    public static final String TAG_INGREDIENT_DIALOG = "add ingredient dialog";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private String mCurrentPhotoPath;
    private Uri mPhotoUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateForumRecipeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.buttonAddIngredient.setOnClickListener(view -> {
            DietItemDialog dialog = new DietItemDialog();
            dialog.show(getSupportFragmentManager(), TAG_INGREDIENT_DIALOG);
            dialog.setOnCloseListener((DietItem newDietItem) -> {
                if (newDietItem == null) {
                    return;
                }
                binding.ingredientsList.addDietItem(newDietItem);
            });
        });

        binding.ingredientsList.setDietChangeListener(dietItemList -> {
            int totalKcal = dietItemList.getTotalKcal();
            binding.editTotalKcal.setText(String.valueOf(totalKcal));
        });

        binding.buttonAddPhoto.setOnClickListener(view -> {
            takePhoto();
        });

        binding.buttonDeletePhoto.setOnClickListener(view -> {
            binding.photo.setImageDrawable(null);
            mPhotoUri = null;
            binding.buttonDeletePhoto.setVisibility(View.INVISIBLE);
        });

        binding.buttonCreationSubmit.setOnClickListener(view -> {
            if (saveRecipe()) {     // recipe creation succeeded
                onBackPressed();
            }
        });
        binding.buttonCreationCancel.setOnClickListener(view -> {
            onBackPressed();
        });
    }

    /**
     * create recipe when user click OK.
     * @return whether a new recipe is created successfully and added to the database.
     */
    private boolean saveRecipe() {
        // Check the required inputs
        binding.errorMsg.setText("");
        String title = binding.editRecipeTitle.getText().toString();
        if (title.equals("")) {
            binding.errorMsg.setText("Title cannot be empty!");
            return false;
        }
        String description = binding.editDescription.getText().toString();
        if (description.equals("")) {
            binding.errorMsg.setText("Description cannot be empty!");
            return false;
        }
        String totalKcalString = binding.editTotalKcal.getText().toString();
        if (totalKcalString.equals("")) {
            binding.errorMsg.setText("Total kCal cannot be empty!");
            return false;
        }
        double totalKcal = Double.parseDouble(totalKcalString);

        // Upload RecipeItem to Firestore
        if (mPhotoUri == null) {
            RecipeItem newRecipe = new RecipeItem(title, description,
                    binding.ingredientsList.getDietItems(), totalKcal, "");
            uploadRecipe(newRecipe);
        } else {
            // Upload photo to Firebase Storage
            StorageReference folderRef = FirebaseStorage.getInstance().getReference()
                    .child(DB_FORUM_RECIPE_IMG_PATH + "/");
            StorageReference photoRef = folderRef.child(mPhotoUri.getLastPathSegment());
            UploadTask uploadTask = photoRef.putFile(mPhotoUri);
            uploadTask.addOnSuccessListener(taskSnapshot -> {
                Task<Uri> downloadUrlTask = photoRef.getDownloadUrl();
                downloadUrlTask.addOnSuccessListener(downloadUrl -> {
                    // Upload Recipe
                    RecipeItem newRecipe = new RecipeItem(title, description,
                            binding.ingredientsList.getDietItems(), totalKcal, downloadUrl.toString());
                    uploadRecipe(newRecipe);
                });
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "upload photo failed", Toast.LENGTH_SHORT).show();
            });
        }
        return true;
    }

    /**
     * Upload the created recipe to firebase. Called by saveRecipe().
     * @param newRecipe a RecipeItem object to be uploaded.
     */
    private void uploadRecipe(RecipeItem newRecipe) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection(DB_FORUM_RECIPE_PATH).add(newRecipe)
                .addOnSuccessListener(documentReference -> {
                    String recipeId = documentReference.getId();
                    Log.d("CreateForumRecipe", "create recipe: " + recipeId);
                    Toast.makeText(this, "recipe created", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "recipe created failed", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * take photo
     */
    private void takePhoto() {
        // check camera permission
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            return;
        }
        // start camera intent
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e("CreateForumRecipe", "Error occurred while creating photo File", ex);
            }
            if (photoFile != null) {
                // param authority below should be: AndroidManifest.xml <provider> android:authorities
                mPhotoUri = FileProvider.getUriForFile(this,
                        "com.example.loseit.fileprovider",
                        photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, mPhotoUri);
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    /**
     * Create a temporary file for the photo. Called by takePhoto().
     * @return a temporary file
     * @throws IOException when failed to create a file
     */
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            binding.photo.setImageURI(mPhotoUri);
            binding.buttonDeletePhoto.setVisibility(View.VISIBLE);
        }
    }

}