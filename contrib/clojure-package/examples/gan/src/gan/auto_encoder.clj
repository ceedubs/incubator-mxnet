(ns gan.auto-encoder
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [org.apache.clojure-mxnet.io :as mx-io]
            [org.apache.clojure-mxnet.ndarray :as ndarray]
            [org.apache.clojure-mxnet.ndarray-api :as ndarray-api]
            [org.apache.clojure-mxnet.image :as image]
            [org.apache.clojure-mxnet.dtype :as dtype]
            [gan.viz :as viz]
            [org.apache.clojure-mxnet.symbol :as sym]
            [org.apache.clojure-mxnet.module :as m]
            [org.apache.clojure-mxnet.eval-metric :as eval-metric]
            [org.apache.clojure-mxnet.initializer :as initializer]
            [org.apache.clojure-mxnet.optimizer :as optimizer])
  (:import (javax.imageio ImageIO)
           (org.apache.mxnet Image NDArray)
           (java.io File)))

(def data-dir "data/")
(def batch-size 100)

(when-not (.exists (io/file (str data-dir "train-images-idx3-ubyte")))
  (sh "../../scripts/get_mnist_data.sh"))


;;; Load the MNIST datasets
;;; note that the label is the same as the image
(def train-data (mx-io/mnist-iter {:image (str data-dir "train-images-idx3-ubyte")
                                       :label (str data-dir "train-images-idx3-ubyte")
                                        ;:input-shape [1 28 28]
                                        :input-shape [784]
                                        :label-shape [784]
                                        :flat true
                                        :batch-size batch-size
                                        :shuffle true}))

(def test-data (mx-io/mnist-iter {:image (str data-dir "train-images-idx3-ubyte")
                                       :label (str data-dir "train-images-idx3-ubyte")
                                       ;;:input-shape [1 28 28]
                                  :input-shape [784]
                                       :batch-size batch-size
                                       :flat true
                                       :shuffle true}))
(def output (sym/variable "input_"))

(defn get-symbol []
  (as-> (sym/variable "input") data
    ;; encode
    (sym/fully-connected "encode1" {:data data :num-hidden 100})
    (sym/activation "sigmoid1" {:data data :act-type "sigmoid"})

    ;; encode
    (sym/fully-connected "encode2" {:data data :num-hidden 50})
    (sym/activation "sigmoid2" {:data data :act-type "sigmoid"})

    ;; decode
    (sym/fully-connected "decode1" {:data data :num-hidden 50})
    (sym/activation "sigmoid3" {:data data :act-type "sigmoid"})

    ;; decode
    (sym/fully-connected "decode2" {:data data :num-hidden 100})
    (sym/activation "sigmoid4" {:data data :act-type "sigmoid"})

    ;;output
    (sym/fully-connected "result" {:data data :num-hidden 784})
    (sym/activation "sigmoid5" {:data data :act-type "sigmoid"})

    (sym/linear-regression-output {:data data :label output})

    #_(sym/make-loss "loss" {:data (sym/- data label)})

    ))

(comment

  (mx-io/provide-data train-data)
  (mx-io/provide-label train-data)
  (mx-io/reset train-data)
  (def my-batch (mx-io/next train-data))
  (def images (mx-io/batch-data my-batch))
  (ndarray/shape (ndarray/reshape (first images) [100 1 28 28]))
  (viz/im-sav {:title "first" :output-path "results/" :x (first images)})
  (viz/im-sav {:title "cm-first" :output-path "results/" :x (ndarray/reshape (first images) [100 1 28 28])})


  (def preds (m/predict-batch my-mod {:data images} ))
  (ndarray/shape (ndarray/reshape (first preds) [100 1 28 28]))
    (viz/im-sav {:title "cm-preds" :output-path "results/" :x (ndarray/reshape (first preds) [100 1 28 28])})
  
  (def my-metric (eval-metric/mse))


  (sym/list-arguments (m/symbol my-mod))
  (def data-desc (first (mx-io/provide-data-desc train-data)))

  (def my-mod (-> (m/module (get-symbol) {:data-names ["input"] :label-names ["input_"]})
                  (m/bind {:data-shapes [(assoc data-desc :name "input")]
                           :label-shapes [(assoc data-desc :name "input_")]})
                  (m/init-params {:initializer  (initializer/uniform 1)})
                  (m/init-optimizer {:optimizer (optimizer/adam {:learning-rage 0.001})})))


  (doseq [epoch-num (range 0 1)]
      (println "starting epoch " epoch-num)
      (mx-io/do-batches
       train-data
       (fn [batch]
         (-> my-mod
             (m/forward {:data (mx-io/batch-data batch) :label (mx-io/batch-data batch)})
             (m/update-metric my-metric (mx-io/batch-data batch))
             (m/backward)
             (m/update))))
      (println "result for epoch " epoch-num " is " (eval-metric/get-and-reset my-metric)))





  (let [mod (m/module (get-sy) {:contexts devs})]
    ;;; note only one function for training
    (m/fit mod {:train-data train-data :eval-data test-data :num-epoch num-epoch})

    ;;high level predict (just a dummy call but it returns a vector of results
    (m/predict mod {:eval-data test-data})

    ;;;high level score (returs the eval values)
    (let [score (m/score mod {:eval-data test-data :eval-metric (eval-metric/accuracy)})]
      (println "High level predict score is " score)))

  


  
  )

;;; Autoencoder network
;;; input -> encode -> middle -> decode -> output