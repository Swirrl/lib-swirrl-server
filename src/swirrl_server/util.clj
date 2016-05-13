(ns swirrl-server.util
  (:import [java.util UUID]))

(defn try-parse-uuid
  "Tries to parse a String into a UUID and returns nil if the
  parse failed."
  [s]
  (when s
    (try
      (UUID/fromString s)
      (catch IllegalArgumentException ex
        nil))))
