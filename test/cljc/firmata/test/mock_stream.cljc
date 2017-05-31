(ns firmata.test.mock-stream
  (:require [firmata.stream.spi :refer [FirmataStream open! close! write listen]])
  #?(:clj
     (:import [java.io ByteArrayInputStream InputStream]
              [java.nio ByteBuffer])))

(defprotocol Bytable
  (to-bytes [this] "Converts the type to bytes"))

#?(:clj
   (extend-protocol Bytable
     Number
     (to-bytes [this] (byte-array 1 (.byteValue this)))

     String
     (to-bytes [this] (.getBytes this "ASCII"))))

#?(:cljs
   (extend-protocol Bytable

     number
     (to-bytes [this]
       (let [b (js/Buffer. 1)]
         (.writeUInt8 b this 0)
         b))

     string
     (to-bytes [this] (js/Buffer. this))))

#?(:clj
   (defn create-byte-stream
     [& more]
     (let [buffer (ByteBuffer/allocate 256)]
       (reduce (fn [^ByteBuffer b ^bytes value] (.put b (to-bytes value))) buffer more)
       (ByteArrayInputStream. (.array buffer)))))

#?(:clj
   (defn create-exception-stream
     [exception]
     (proxy [InputStream] []
       (read
         ([] (throw exception))
         ([byte-arr]
          (proxy-super read byte-arr))
         ([byte-arr off len]
          (proxy-super read byte-arr off len))))))

#?(:cljs
   (defn create-byte-stream
     [& more]
     ((.-concat js/Buffer) (to-array (map #(to-bytes %) more)))))

(defrecord MockClientStream [^clojure.lang.Atom state last-write]
    FirmataStream
    (open! [this]
      (swap! state  assoc :is-open? true)
      this)

    (close! [this]
      (swap! state  assoc :is-open? false)
      this)

    (listen [_ handler]
      (swap! state assoc :handler handler)
      (handler (create-byte-stream 0xF9 9 9))
      (handler (create-byte-stream 0xF0 0x79 9 9 "Test Firmware" 0xF7))
      nil)

    (write [_ data]
      (if-let [ex (:exception @state)]
        (throw ex)
        (reset! last-write data))))

(defn create-mock-stream []
    (->MockClientStream (atom {}) (atom nil)))

(defn- state [client k]
  (get (deref (:state client)) k))

(defn receive-bytes [client & more]
  ((state client :handler) (apply create-byte-stream more)))

#?(:clj
   (defn throw-on-read [client exception]
     ((state client :handler) (create-exception-stream exception))))

(defn throw-on-write [client exception]
  (swap! (:state client) assoc :exception exception))

(defn last-write [client]
  (-> client :last-write deref vector flatten vec))

(defn is-open? [client]
  (state client :is-open?))
