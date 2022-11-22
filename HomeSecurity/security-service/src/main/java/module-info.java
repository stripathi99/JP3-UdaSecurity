module com.udacity.security {
  requires com.udacity.image;
  requires miglayout;
  requires java.desktop;
  requires java.prefs;
  requires com.google.common;
  requires com.google.gson;
  opens com.udacity.security.data to com.google.gson;
}