// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps.servlets;

import com.google.sps.servlets.Utilities;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.ServingUrlOptions;
import com.google.appengine.api.datastore.Entity;
import java.net.MalformedURLException;
import java.net.URL;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet that stores new comments. */
@WebServlet("/new-data")
public class NewCommentServlet extends HttpServlet {

  private static final DatastoreService DATASTORE = 
      DatastoreServiceFactory.getDatastoreService();
  private static final UserService USERSERVICE =
      UserServiceFactory.getUserService();
  private static final BlobstoreService BLOBSTORESERVICE = 
      BlobstoreServiceFactory.getBlobstoreService();
  private static final ImagesService IMAGESSERVICE = 
      ImagesServiceFactory.getImagesService();
  private static final BlobInfoFactory BLOBINFOFACTORY = new BlobInfoFactory();
    
  @Override
  public void doPost(HttpServletRequest request, 
                     HttpServletResponse response) throws IOException {
    // Retrieve information from the form and add timestamp
    final String name = Utilities.getParameterWithDefault(
      request, "user-name", "Anonymous");
    final String latitude = Utilities.getParameterWithDefault(
      request, "lat", "unknown");
    final String longitude = Utilities.getParameterWithDefault(
      request, "long", "unknown");
    final String job = Utilities.getParameterWithDefault(
      request, "jobs", "Other");
    final String email = USERSERVICE.getCurrentUser().getEmail();
    final String comment = Utilities.getParameterWithDefault(
      request, "visitor-comment", "");
    String imageUrl = "";
    final long timestamp = System.currentTimeMillis();
    
    BlobKey blobKey = getBlobKey(request, "image");
    // Get uploaded file url if user uploaded an image file
    // Otherwise, keep imageUrl empty
    if (blobKey != null) {
      try {
        imageUrl = getUploadedFileUrl(blobKey);
      } catch (IllegalArgumentException e) {
        imageUrl = "";
        System.err.println("Please upload an image file");
      }
    }

    // Store the information if comment is non-empty
    if (!comment.isEmpty()) {
      Entity messageEntity = new Entity("Message");
      messageEntity.setProperty("name", name);
      messageEntity.setProperty("lat", latitude);
      messageEntity.setProperty("long", longitude);
      messageEntity.setProperty("job", job);
      messageEntity.setProperty("email", email);
      messageEntity.setProperty("comment", comment);
      messageEntity.setProperty("imageUrl", imageUrl);
      messageEntity.setProperty("timestamp", timestamp);
      DATASTORE.put(messageEntity);
    }

    response.sendRedirect("/index.html");
  }

  /** Returns a URL that points to the uploaded file */
  private String getUploadedFileUrl(BlobKey blobKey) {
    ServingUrlOptions options = ServingUrlOptions.Builder.withBlobKey(blobKey);

    // To support running in Google Cloud Shell with AppEngine's devserver, 
    // we must use the relative path to the image, 
    // rather than the path returned by imagesService which contains a host.
    try {
      URL url = new URL(IMAGESSERVICE.getServingUrl(options));
      return url.getPath();
    } catch (MalformedURLException e) {
      return IMAGESSERVICE.getServingUrl(options);
    }
  }

  /**
   * Returns the BlobKey that points to the file uploaded by the user, or null if the user didn't
   * upload a file.
   */
  private BlobKey getBlobKey(HttpServletRequest request, String name) {
    Map<String, List<BlobKey>> blobs = BLOBSTORESERVICE.getUploads(request);
    List<BlobKey> blobKeys = blobs.get(name);

    // User submitted form without selecting a file, so we can't get a BlobKey. (dev server)
    if (blobKeys == null || blobKeys.isEmpty()) {
      return null;
    }

    // Our form only contains a single file input, so get the first index.
    BlobKey blobKey = blobKeys.get(0);

    // User submitted form without selecting a file, so the BlobKey is empty. (live server)
    BlobInfo blobInfo = BLOBINFOFACTORY.loadBlobInfo(blobKey);
    if (blobInfo.getSize() == 0) {
      BLOBSTORESERVICE.delete(blobKey);
      return null;
    }

    return blobKey;
  }
}