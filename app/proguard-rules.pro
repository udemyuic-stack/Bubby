# No extra keep rules needed: the app has no reflection and no third-party libraries
# (class names are only read for error messages, e.g. javaClass.simpleName of framework exceptions).
# Classes named in the manifest keep their names through AAPT's generated rules; everything else may move into the
# root package with short names, which makes the dex a little smaller.
-repackageclasses
