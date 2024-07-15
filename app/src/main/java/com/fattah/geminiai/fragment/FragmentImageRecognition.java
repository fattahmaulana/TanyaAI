package com.fattah.geminiai.fragment;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.fattah.geminiai.R;
import com.fattah.geminiai.utils.CustomLoadingDialog;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.BlockThreshold;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.GenerationConfig;
import com.google.ai.client.generativeai.type.HarmCategory;
import com.google.ai.client.generativeai.type.SafetySetting;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;
import java.util.concurrent.Executor;

public class  FragmentImageRecognition extends Fragment {

    private static final int SELECT_IMAGE_REQUEST = 1;
    private static final int CAPTURE_IMAGE_REQUEST = 2;
    private Bitmap image;
    private ImageView imageView;
    private TextInputEditText queryEditText;
    private TextView responseTextView;
    private Button sendQueryButton, selectImageButton;
    private CustomLoadingDialog progressDialog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_image_recognition, container, false);

        queryEditText = rootView.findViewById(R.id.queryEditText);
        responseTextView = rootView.findViewById(R.id.modelResponseTextView);
        sendQueryButton = rootView.findViewById(R.id.sendPromptButton);
        selectImageButton = rootView.findViewById(R.id.selectImageButton);
        imageView = rootView.findViewById(R.id.imageView);

        progressDialog = new CustomLoadingDialog(getContext());

        sendQueryButton.setOnClickListener(v -> {
            GeminiPro model = new GeminiPro();

            String query = queryEditText.getText().toString();
            progressDialog.show();

            responseTextView.setText("");
            queryEditText.setText("");

            model.getResponse(query, image, new ResponseCallback() {
                @Override
                public void onResponse(String response) {
                    responseTextView.setText(response);
                    progressDialog.dismiss();
                }

                @Override
                public void onError(Throwable throwable) {
                    Toast.makeText(getActivity(), "Error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                    progressDialog.dismiss();
                }
            });
        });

        selectImageButton.setOnClickListener(v -> showImageSourceDialog());

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        return rootView;
    }

    private void showImageSourceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Pilih Gambar Dari");
        builder.setItems(new String[]{"Galeri", "Kamera"}, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(intent, SELECT_IMAGE_REQUEST);
                } else if (which == 1) {
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(intent, CAPTURE_IMAGE_REQUEST);
                }
            }
        });
        builder.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SELECT_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap originalImage = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), imageUri);

                int originalWidth = originalImage.getWidth();
                int originalHeight = originalImage.getHeight();

                int targetWidth = (int) (originalWidth * 0.5);
                int targetHeight = (int) (originalHeight * 0.5);

                image = Bitmap.createScaledBitmap(originalImage, targetWidth, targetHeight, false);
                imageView.setImageBitmap(originalImage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (requestCode == CAPTURE_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            if (extras != null) {
                Bitmap originalImage = (Bitmap) extras.get("data");

                int originalWidth = originalImage.getWidth();
                int originalHeight = originalImage.getHeight();

                int targetWidth = (int) (originalWidth * 0.5);
                int targetHeight = (int) (originalHeight * 0.5);

                image = Bitmap.createScaledBitmap(originalImage, targetWidth, targetHeight, false);
                imageView.setImageBitmap(originalImage);
            }
        }
    }

    public interface ResponseCallback {
        void onResponse(String response);
        void onError(Throwable throwable);
    }

    private static class BuildConfig {
        static String apiKey = "ISI_API_GEMINI";
    }

    private static class GeminiPro {
        void getResponse(String query, Bitmap image, ResponseCallback callback) {
            GenerativeModelFutures model = getModel();

            Content content = new Content.Builder()
                    .addText(query)
                    .addImage(image)
                    .build();
            Executor executor = Runnable::run;

            ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
            Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    String resultText = result.getText();
                    callback.onResponse(resultText);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    throwable.printStackTrace();
                    callback.onError(throwable);
                }
            }, executor);
        }

        private GenerativeModelFutures getModel() {
            String apiKey = BuildConfig.apiKey;

            SafetySetting harassmentSafety = new SafetySetting(HarmCategory.HARASSMENT,
                    BlockThreshold.ONLY_HIGH);

            GenerationConfig.Builder configBuilder = new GenerationConfig.Builder();
            configBuilder.temperature = 0.9f;
            configBuilder.topK = 16;
            configBuilder.topP = 0.1f;
            GenerationConfig generationConfig = configBuilder.build();

            GenerativeModel gm = new GenerativeModel(
                    "gemini-pro-vision",
                    apiKey,
                    generationConfig,
                    Collections.singletonList(harassmentSafety)
            );

            return GenerativeModelFutures.from(gm);
        }
    }
}
