package me.ianterry.face;

import android.app.ActionBar;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Resources;
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
import android.support.constraint.Constraints;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.widget.ShareButton;
import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.*;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.DefaultLogger;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.Twitter;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterConfig;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.identity.TwitterAuthClient;
import com.twitter.sdk.android.tweetcomposer.ComposerActivity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static FaceServiceClient faceServiceClient =
            new FaceServiceRestClient("https://westcentralus.api.cognitive.microsoft.com/face/v1.0", "4fd49bdfb85c47f9b26abfebb03feb82");

    private static final String KEY_BITMAP = "bitmap";
    private static final String KEY_URI = "uri";
    private static final String KEY_SHARE_BOOL = "share_bool";

    private ImageView mImageView;
    //private Button mShareButton;
    public final String TAG = "attributeMethod"; //just for debugging
    private final int PICK_IMAGE = 1;
    static final int REQUEST_TAKE_PHOTO = 2;
    private static ProgressDialog progress;
    private Bitmap mBitmap;
    private Uri photoURI;
    private boolean isShareAvail = false;

    private TwitterAuthClient client;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null)
        {
            //mBitmap = savedInstanceState.getParcelable(KEY_BITMAP);
            photoURI = savedInstanceState.getParcelable(KEY_URI);
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
                mImageView = (ImageView) findViewById(R.id.image);
                mImageView.setImageBitmap(mBitmap);
            }

            if (isShareAvail)
            {
                //stuff for the facebook share button
                SharePhoto photo = new SharePhoto.Builder()
                        .setBitmap(mBitmap)
                        .build();
                SharePhotoContent content = new SharePhotoContent.Builder()
                        .addPhoto(photo)
                        .build();
                ShareButton shareButton = (ShareButton) findViewById(R.id.fb_share_button);
                shareButton.setShareContent(content);
            }


        }

        TwitterConfig config = new TwitterConfig.Builder(this)
                .logger(new DefaultLogger(Log.DEBUG))
                .twitterAuthConfig(new TwitterAuthConfig("GXyyodWmlXOsq01mUPZhaIATo", "aDJ0C8MO8YapJFR2zSHZ6z7RpkeRq6r92bAic1j9kTRfXI6XI3"))
                .debug(true)
                .build();
        Twitter.initialize(config);

        Button mCaptureButton;
        Button mProcessButton;
        Button mBrowseButton;
        Button mTwitterShareButton;

        mBrowseButton = findViewById(R.id.btn_browse);
        mBrowseButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
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
                    detectAndFrame(mBitmap);
                }
            }
        });

        mTwitterShareButton = findViewById(R.id.btn_twitter_share);
        mTwitterShareButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                final TwitterSession session = TwitterCore.getInstance().getSessionManager()
                        .getActiveSession();
                if (session != null)
                {
                    final Intent intent = new ComposerActivity.Builder(MainActivity.this)
                            .session(session)
                            .image(photoURI)
                            .text("Test")
                            .hashtags("#test")
                            .createIntent();
                    startActivity(intent);
                }
                else if (session == null)
                {
                    authenticateUser();
                }

            }
        });
        /*mShareButton = findViewById(R.id.btn_share);
        mShareButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){

        });*/

        progress = new ProgressDialog(this);
    }


    //method to share image using Twitter Native Kit composer
    private void shareUsingNativeComposer(TwitterSession session) {
        Intent intent = new ComposerActivity.Builder(this)
                .session(session)//Set the TwitterSession of the User to Tweet
                .image(photoURI)//Attach an image to the Tweet
                .text("This is Native Kit Composer Tweet!!")//Text to prefill in composer
                .hashtags("#android")//Hashtags to prefill in composer
                .createIntent();//finally create intent
        startActivity(intent);
    }



    //method call to authenticate user
    private void authenticateUser() {
        client = new TwitterAuthClient();//init twitter auth client
        client.authorize(this, new Callback<TwitterSession>() {
            @Override
            public void success(Result<TwitterSession> twitterSessionResult) {
                //if user is successfully authorized start sharing image
                Toast.makeText(MainActivity.this, "Login successful.", Toast.LENGTH_SHORT).show();
                shareUsingNativeComposer(twitterSessionResult.data);
            }

            @Override
            public void failure(TwitterException e) {
                //if user failed to authorize then show toast
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Failed to authenticate by Twitter. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        //STUFF FOR BROWSE
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null)
        {
            Uri uri = data.getData();
            photoURI = data.getData();
            try
            {
                mBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                mImageView = (ImageView) findViewById(R.id.image);
                mImageView.setImageBitmap(mBitmap);

                //grey out the share button
                isShareAvail = false;
                ShareButton shareButton = (ShareButton) findViewById(R.id.fb_share_button);
                shareButton.setShareContent(null);
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
                    mImageView = (ImageView) findViewById(R.id.image);
                    mImageView.setImageBitmap(mBitmap);

                    //grey out the share button
                    isShareAvail = false;
                    ShareButton shareButton = (ShareButton) findViewById(R.id.fb_share_button);
                    shareButton.setShareContent(null);
                }
                catch(NullPointerException e)
                {
                    //Log.e(TAG, "there it is");
                    e.printStackTrace();
                }
                catch(IOException ioe)
                {
                    //Log.e(TAG, "ok io exception");
                    ioe.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState)
    {
        super.onSaveInstanceState(savedInstanceState);
        //savedInstanceState.putParcelable(KEY_BITMAP, mBitmap);
        savedInstanceState.putParcelable(KEY_URI, photoURI);
        savedInstanceState.putBoolean(KEY_SHARE_BOOL, isShareAvail);
    }

    private void detectAndFrame(final Bitmap myBitmap)
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        myBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

        new detectTask(this, myBitmap).execute(inputStream);
    }

    private Bitmap drawFaceRectangleOnBitmap(Bitmap myBitmap, Face[] faces)
    {
        Bitmap bitmap = myBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);
        int strokeWidth = 8;
        paint.setStrokeWidth(strokeWidth);
        if(faces != null)
        {
            for(Face face: faces)
            {
                FaceRectangle faceRectangle = face.faceRectangle;
               /* canvas.drawRect(faceRectangle.left,  //this draws the rectangle
                                faceRectangle.top,
                                faceRectangle.left + faceRectangle.width,
                                faceRectangle.top + faceRectangle.height,
                                 paint);*/

                //create emoji imageview
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

                mFaceImageView.setImageBitmap(faceBitmap);

                ConstraintLayout layout = findViewById(R.id.constraint_layout);
                ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT);

                mFaceImageView.setLayoutParams(lp);
                ConstraintSet set = new ConstraintSet();
                set.clone(layout);
                set.connect(mFaceImageView.getId(), ConstraintSet.TOP, mImageView.getId(), ConstraintSet.TOP);
                set.connect(mFaceImageView.getId(), ConstraintSet.RIGHT, mImageView.getId(), ConstraintSet.RIGHT);
                set.connect(mFaceImageView.getId(), ConstraintSet.BOTTOM, mImageView.getId(), ConstraintSet.BOTTOM);
                set.connect(mFaceImageView.getId(), ConstraintSet.LEFT, mImageView.getId(), ConstraintSet.LEFT);
                set.applyTo(layout);

                //this draws the emojis in the right spot
                Bitmap scaledFace = Bitmap.createScaledBitmap(faceBitmap, faceRectangle.width + (faceRectangle.width/8), faceRectangle.height + (int)(faceRectangle.height/2f), true);  //scales the emoji size
                canvas.drawBitmap(scaledFace, faceRectangle.left - ((faceRectangle.width/9)/2), faceRectangle.top - ((int)(faceRectangle.height/2)/2), paint);  //draws the emoji on the face

                Log.d("ok", "faceRect-left: " + faceRectangle.left + " faceRect-top: " + faceRectangle.top);
                Log.d("ok", "faceRect-width: " + faceRectangle.width + " facRect-height: " + faceRectangle.height);
                Log.d("ok", "emoji-width: " + mImageView.getLayoutParams().width + " emoji-height: " + mImageView.getLayoutParams().height);

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
        else if (emotion.surprise > emotionValue)
        {
            emotionValue = emotion.surprise;
            emotionType = "Surprise";
        }
        return emotionType /*+ emotionValue*/;
    }

    //String mCurrentPhotoPath;

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

        // Save a file: path for use with ACTION_VIEW intents
        //mCurrentPhotoPath = image.getAbsolutePath();
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
                // Error occurred while creating the File
            //...
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                 photoURI = FileProvider.getUriForFile(this,
                        "com.example.android.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }


    private static class detectTask extends AsyncTask<InputStream, String, Face[]>
    {
        //private ProgressDialog progress = new ProgressDialog(this);
        //ProgressDialog progress;
        private WeakReference<MainActivity> activityReference;

        Bitmap mMyBitmap;

        public detectTask(MainActivity context, Bitmap myBitmap)
        {
            activityReference = new WeakReference<MainActivity>(context);
            mMyBitmap = myBitmap;
        }

        //ProgressDialog progress0 = activityReference.get().progress;

        //ImageView mImageView0 = activityReference.get().mImageView;

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

                //File storageDir = mActivity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                File storageDir = new File(mActivity.getApplicationContext().getFilesDir(), "emojify_pic");

                File image = File.createTempFile(
                        imageFileName,  /* prefix */
                        ".jpg",         /* suffix */
                        storageDir      /* directory */
                );
                FileOutputStream fos = new FileOutputStream(image);
                fos.write(bitmapData);
                fos.flush();
                fos.close();
                mCurrentPhotoPath = image.getAbsolutePath();
                Log.e("photo path", "bicycle: " + mCurrentPhotoPath);
                mActivity.photoURI = Uri.fromFile(image);
            } catch (IOException ex) {
                // Error occurred while creating the File
                ex.printStackTrace();
            }
            // Continue only if the File was successfully created

            /*try
            {
                mActivity.mBitmap = MediaStore.Images.Media.getBitmap(mActivity.getContentResolver(), mActivity.photoURI);
            }
            catch(IOException e)
            {
                e.printStackTrace();
            }*/

///////////////
            //toast showing # faces detected
            Toast toast = Toast.makeText(mActivity.getApplicationContext(), "" + faces.length + " faces detected", Toast.LENGTH_SHORT);
            toast.show();

            //stuff for the facebook share button
            SharePhoto photo = new SharePhoto.Builder()
                    .setBitmap(mActivity.mBitmap)
                    .build();
            SharePhotoContent content = new SharePhotoContent.Builder()
                    .addPhoto(photo)
                    .build();
            ShareButton shareButton = (ShareButton) mActivity.findViewById(R.id.fb_share_button);
            shareButton.setShareContent(content);

            mActivity.isShareAvail = true; //this allows share button to remain usable after screen rotation
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
            //return new Face[0];
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
