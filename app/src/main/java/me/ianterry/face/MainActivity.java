package me.ianterry.face;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.Toast;

import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.widget.ShareDialog;
import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.*;
import com.twitter.sdk.android.core.DefaultLogger;
import com.twitter.sdk.android.core.Twitter;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterConfig;
import com.twitter.sdk.android.core.identity.TwitterAuthClient;
import com.twitter.sdk.android.tweetcomposer.TweetComposer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    //for MS Face API
    private static FaceServiceClient faceServiceClient =
            new FaceServiceRestClient("https://westcentralus.api.cognitive.microsoft.com/face/v1.0", "4fd49bdfb85c47f9b26abfebb03feb82");

    private static final String KEY_URI = "uri";
    private static final String KEY_CONTENT_URI = "content_uri";
    private static final String KEY_SHARE_BOOL = "share_bool";

    private final String[] shareChoices = {"Facebook", "Twitter"};
    private final Integer[] shareIcons = {R.drawable.flogo_rgb_hex_24, R.drawable.twitter_social_icon_rounded_square_color_24};

    private ImageView mImageView;
    public final String TAG = "attributeMethod"; //just for debugging
    private final int PICK_IMAGE = 1;
    static final int REQUEST_TAKE_PHOTO = 2;
    private static ProgressDialog progress;
    private Bitmap mBitmap;
    private Uri photoURI;
    private Uri mContentURI;
    private boolean isShareAvail = false;
    private static Button mShareButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null)
        {
            photoURI = savedInstanceState.getParcelable(KEY_URI);
            mContentURI = savedInstanceState.getParcelable(KEY_CONTENT_URI);
            isShareAvail = savedInstanceState.getBoolean(KEY_SHARE_BOOL);

            try
            {
                if (photoURI != null)
                {
                    mBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), photoURI);
                }
            }
            catch(IOException | NullPointerException e)
            {
                e.printStackTrace();
            }

            if (mBitmap != null)
            {
                mImageView = findViewById(R.id.image);
                mImageView.setImageBitmap(mBitmap);
            }
        }

        TwitterConfig config = new TwitterConfig.Builder(this)
                .logger(new DefaultLogger(Log.DEBUG))
                .twitterAuthConfig(new TwitterAuthConfig("GXyyodWmlXOsq01mUPZhaIATo", "aDJ0C8MO8YapJFR2zSHZ6z7RpkeRq6r92bAic1j9kTRfXI6XI3"))
                .debug(true)
                .build();
        Twitter.initialize(config);

        final Button mCaptureButton;
        Button mProcessButton;
        Button mBrowseButton;

        mBrowseButton = findViewById(R.id.btn_browse);
        mBrowseButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                //This stuff allows user to select image from device storage
                Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
                galleryIntent.setType("image/*");
                startActivityForResult(Intent.createChooser(galleryIntent, "Select Picture"), PICK_IMAGE);
            }
        });

        mCaptureButton = findViewById(R.id.capture_btn);
        mCaptureButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view)
            {
                dispatchTakePictureIntent();
            }
        });

        mProcessButton = findViewById(R.id.btn_process);
        mProcessButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mBitmap != null)
                {
                    detectAndPaintEmoji(mBitmap);
                }
                else if (mBitmap == null)
                {
                    Toast.makeText(MainActivity.this, "No image to process", Toast.LENGTH_SHORT).show();
                }
            }
        });

        shareButtonMethod(isShareAvail);

        progress = new ProgressDialog(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        //STUFF FOR BROWSE
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null)
        {
            photoURI = data.getData();
            try
            {
                mBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), photoURI);
                mImageView = (ImageView) findViewById(R.id.image);
                mImageView.setImageBitmap(mBitmap);

                //grey out the share button
                isShareAvail = false;
                shareButtonMethod(false);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        //STUFF FOR TAKE PHOTO
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK)
        {
            if (photoURI != null)
            {
                Uri imgUri = photoURI;

                try
                {
                    mBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imgUri);
                    mImageView = findViewById(R.id.image);
                    mImageView.setImageBitmap(mBitmap);

                    //grey out the share button
                    isShareAvail = false;
                    shareButtonMethod(false);
                }
                catch(NullPointerException | IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState)
    {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable(KEY_URI, photoURI);
        savedInstanceState.putParcelable(KEY_CONTENT_URI, mContentURI);  //content URI is needed for tweet composer
        savedInstanceState.putBoolean(KEY_SHARE_BOOL, isShareAvail);
    }

    private void detectAndPaintEmoji(final Bitmap myBitmap)
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        myBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

        new detectTask(this, myBitmap).execute(inputStream);
    }

    private void shareButtonMethod(boolean bool)
    {

        mShareButton = findViewById(R.id.btn_share);
        if (bool)
        {
            mShareButton.setEnabled(true);
            mShareButton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View view)
                {
                    ListAdapter adapter = new ArrayAdapterWithIcon(MainActivity.this, shareChoices, shareIcons);

                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle(R.string.choose_platform)
                            .setAdapter(adapter, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    if (shareChoices[i] == "Facebook")
                                    {
                                        SharePhoto photo = new SharePhoto.Builder()
                                                .setBitmap(mBitmap)
                                                .build();
                                        SharePhotoContent content = new SharePhotoContent.Builder()
                                                .addPhoto(photo)
                                                .build();
                                        ShareDialog.show(MainActivity.this, content);
                                    }
                                    else if (shareChoices[i] == "Twitter")
                                    {
                                        TweetComposer.Builder builder = new TweetComposer.Builder(MainActivity.this)
                                                .text("Picture created with #EmojiPhoto app for Android.")
                                                .image(mContentURI);
                                        builder.show();
                                    }
                                }
                            });
                    builder.setNegativeButton("Cancel", null);
                    builder.create();
                    builder.show();
                }
            });
        }
        else if (!bool)
        {
            mShareButton.setEnabled(false);
        }

    }

    private Bitmap drawFaceRectangleOnBitmap(Bitmap myBitmap, Face[] faces)
    {
        Bitmap bitmap = myBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        if(faces != null)
        {
            for(Face face: faces) //for each of the faces detected
            {
                FaceRectangle faceRectangle = face.faceRectangle;  //get rectangle binding face

                ImageView mFaceImageView;
                mFaceImageView = new ImageView(getApplicationContext());
                mFaceImageView.setId(View.generateViewId());
                Bitmap faceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.error);  //initialize face to error face in case detection fails

                FaceAttribute attribute = face.faceAttributes;
                //pick the proper emoji based on emotion
                String emotionString = getEmotion(attribute.emotion);
                if (emotionString == "Anger")
                {
                    faceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.angry);  //the emoji image
                }
                else if (emotionString == "Contempt")
                {
                    faceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.contempt);  //the emoji image
                }
                else if (emotionString == "Disgust")
                {
                    faceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.disgust);  //the emoji image
                }
                else if (emotionString == "Fear")
                {
                    faceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.fear);  //the emoji image
                }
                else if (emotionString == "Happiness")
                {
                    faceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.happy);  //the emoji image
                }
                else if (emotionString == "Neutral")
                {
                    faceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.neutral);  //the emoji image
                }
                else if (emotionString == "Sadness")
                {
                    faceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.sadness);  //the emoji image
                }
                else if (emotionString == "Surprise")
                {
                    faceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.surprise);  //the emoji image
                }

                //this draws the emoji over the detected face
                Bitmap scaledFace = Bitmap.createScaledBitmap(faceBitmap, faceRectangle.width + (faceRectangle.width/8), faceRectangle.height + (int)(faceRectangle.height/2f), true);  //scales the emoji size
                canvas.drawBitmap(scaledFace, faceRectangle.left - ((faceRectangle.width/9)/2), faceRectangle.top - ((int)(faceRectangle.height/2)/2), paint);  //draws the emoji on the face
            }
        }
        return bitmap;
    }

    /* THIS IS USED FOR DEBUGGING
    ------------------------------
     -----------------------------
    private void attributeMethod(Face[] faces)
    {
        for(Face face: faces)
        {
            FaceAttribute attribute = face.faceAttributes;
            //Log.d(TAG, "age: " + attribute.age);
            Log.d(TAG, "Emotion: " + getEmotion(attribute.emotion));
            Log.d(TAG, "anger: " + attribute.emotion.anger);
            Log.d(TAG, "contempt: " + attribute.emotion.contempt);
            Log.d(TAG, "disgust: " + attribute.emotion.disgust);
            Log.d(TAG, "fear: " + attribute.emotion.fear);
            Log.d(TAG, "happiness: " + attribute.emotion.happiness);
            Log.d(TAG, "neutral: " + attribute.emotion.neutral);
            Log.d(TAG, "sadness: " + attribute.emotion.sadness);
            Log.d(TAG, "surprise: " + attribute.emotion.surprise);
        }
    }*/

    private String getEmotion(Emotion emotion)
    {
        String emotionType = "";
        double emotionValue = 0.0;
        if (emotion.anger > emotionValue)
        {
            emotionValue = emotion.anger;
            emotionType = "Anger";
        }
        if (emotion.contempt > emotionValue)
        {
            emotionValue = emotion.contempt;
            emotionType = "Contempt";
        }
        if (emotion.disgust > emotionValue)
        {
            emotionValue = emotion.disgust;
            emotionType = "Disgust";
        }
        if (emotion.fear > emotionValue)
        {
            emotionValue = emotion.fear;
            emotionType = "Fear";
        }
        if (emotion.happiness > emotionValue)
        {
            emotionValue = emotion.happiness;
            emotionType = "Happiness";
        }
        if (emotion.neutral > emotionValue)
        {
            emotionValue = emotion.neutral;
            emotionType = "Neutral";
        }
        if (emotion.sadness > emotionValue)
        {
            emotionValue = emotion.sadness;
            emotionType = "Sadness";
        }
        if (emotion.surprise > emotionValue)
        {
            emotionValue = emotion.surprise;
            emotionType = "Surprise";
        }
        return emotionType /*+ emotionValue*/;
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        return image;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                 photoURI = FileProvider.getUriForFile(this,
                        "me.ianterry.face.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }


    private static class detectTask extends AsyncTask<InputStream, String, Face[]>
    {
        private WeakReference<MainActivity> activityReference;

        Bitmap mMyBitmap;

        public detectTask(MainActivity context, Bitmap myBitmap)
        {
            activityReference = new WeakReference<>(context);
            mMyBitmap = myBitmap;
        }

        @Override
        protected void onPostExecute(Face[] faces) {
            MainActivity mActivity = activityReference.get();
            if (mActivity == null || mActivity.isFinishing())
            {
                return;
            }

            progress.dismiss();
            if(faces == null)
            {
                return;
            }
            mActivity.mBitmap = mActivity.drawFaceRectangleOnBitmap(mMyBitmap, faces);
            mActivity.mImageView.setImageBitmap(mActivity.mBitmap);

            ///////////// the purpose of this next stuff is to get a URI for the Bitmap so that we can save the uri across screen rotation
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            mActivity.mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
            byte[] bitmapData = bytes.toByteArray();

            String mCurrentPhotoPath = null;
            try {
                // Create an image file name
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String imageFileName = "JPEG_" + timeStamp + "_";

                File storageDir = new File(mActivity.getApplicationContext().getFilesDir(), "emojiphoto_pic");
                storageDir.mkdirs();

                File image = File.createTempFile(
                        imageFileName,  /* prefix */
                        ".jpg",         /* suffix */
                        storageDir      /* directory */
                );
                FileOutputStream fos = new FileOutputStream(image);
                fos.write(bitmapData);
                fos.flush();
                fos.close();
                mCurrentPhotoPath = image.getAbsolutePath();  //for debugging
                mActivity.photoURI = Uri.fromFile(image);
                mActivity.mContentURI = FileProvider.getUriForFile(mActivity.getApplicationContext(), BuildConfig.APPLICATION_ID + ".fileprovider", image);  //this is a content uri used for twitter share
            } catch (IOException ex) {
                // Error occurred while creating the File
                ex.printStackTrace();
            }
            ///////////////

            //toast showing # faces detected
            Toast toast = Toast.makeText(mActivity.getApplicationContext(), "" + faces.length + " face(s) detected", Toast.LENGTH_SHORT);
            toast.show();

            if (faces.length > 0)
            {
                mActivity.isShareAvail = true; //this allows share button to remain usable after screen rotation
            }

            mActivity.shareButtonMethod(mActivity.isShareAvail);
        }

        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            progress.show();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            progress.setMessage(values[0]);
        }

        @Override
        protected Face[] doInBackground(InputStream... inputStreams) {
            try
            {

                publishProgress("Detecting...");
                Face[] result = faceServiceClient.detect(inputStreams[0], true, false, new FaceServiceClient.FaceAttributeType[]{FaceServiceClient.FaceAttributeType.Emotion});
                if(result == null)
                {
                    publishProgress("Detection finished. Nothing detected.");
                    return null;
                }
                publishProgress(String.format("Detection Finished. %d face(s) detected", result.length));
                return result;
            }
            catch (Exception e)
            {
                publishProgress("Detection failed.");
                return null;
            }
        }
    }

}
