package org.deeplearning4j.nn.layers.normalization;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.BaseLayer;
import org.deeplearning4j.nn.params.BatchNormalizationParamInitializer;
import org.deeplearning4j.optimize.api.ConvexOptimizer;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.broadcast.BroadcastAddOp;
import org.nd4j.linalg.api.ops.impl.broadcast.BroadcastDivOp;
import org.nd4j.linalg.api.ops.impl.broadcast.BroadcastMulOp;
import org.nd4j.linalg.api.ops.impl.broadcast.BroadcastSubOp;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.*;

/**
 * Batch normalization layer.
 * http://arxiv.org/pdf/1410.7455v8.pdf
 *
 * @author Adam Gibson
 */
public class BatchNormalization extends BaseLayer<ConvolutionLayer> {
    private INDArray std;
    private NeuralNetConfiguration conf;
    private int index = 0;
    private List<IterationListener> listeners = new ArrayList<>();
    private Map<String,INDArray> params = new LinkedHashMap<>();
    private int[] shape;
    private Gradient gradient;
    private INDArray xHat;

    public BatchNormalization(NeuralNetConfiguration conf) {
        super(conf);
    }

    @Override
    public double calcL2() {
        return 0;
    }

    @Override
    public double calcL1() {
        return 0;
    }

    @Override
    public Type type() {
        return Type.CONVOLUTIONAL;
    }

    @Override
    public Gradient error(INDArray input) {
        return null;
    }

    @Override
    public INDArray derivativeActivation(INDArray input) {
        return null;
    }

    @Override
    public Gradient calcGradient(Gradient layerError, INDArray indArray) {
        return null;
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon) {
        epsilon = epsilon.reshape(shape);
        int m = shape[0] * shape[2];
        INDArray gBeta = epsilon.sum(0,2);
        INDArray gammGradient = getParam(BatchNormalizationParamInitializer.GAMMA_GRADIENT);
        Nd4j.getExecutioner().execAndReturn(new BroadcastAddOp(gammGradient, gBeta, gammGradient, 1));
        INDArray newGamma = epsilon.reshape(xHat.shape()).mul(xHat).sum(0, 2);
        Nd4j.getExecutioner().execAndReturn(new BroadcastAddOp(gammGradient,newGamma,gammGradient,1));

        INDArray coefficients = getParam(BatchNormalizationParamInitializer.GAMMA).div(std);
        gBeta.divi(m);
        getParam(BatchNormalizationParamInitializer.GAMMA_GRADIENT).divi(m);
        INDArray toMuli = epsilon.reshape(xHat.shape()).sub(xHat);
        INDArray otherMuli = Nd4j.getExecutioner().execAndReturn(new BroadcastMulOp(toMuli,gammGradient,toMuli,-1));
        INDArray sub = Nd4j.getExecutioner().execAndReturn(new BroadcastSubOp(otherMuli,gBeta,otherMuli,-1));
        INDArray ret = Nd4j.getExecutioner().execAndReturn(new BroadcastMulOp(sub,coefficients,sub,-1));

        ret = ret.reshape(shape);
        Gradient g = new DefaultGradient();
        // g.setGradientFor(BatchNormalizationParamInitializer.GAMMA_GRADIENT,getParam(BatchNormalizationParamInitializer.GAMMA_GRADIENT));
        //g.setGradientFor(BatchNormalizationParamInitializer.BETA_GRADIENT,getParam(BatchNormalizationParamInitializer.BETA_GRADIENT));
        this.gradient = g;
        return new Pair<>(g,ret);
    }

    @Override
    public void merge(Layer layer, int batchSize) {

    }

    @Override
    public INDArray activationMean() {
        return null;
    }

    @Override
    public void update(Gradient gradient) {

    }

    @Override
    public void fit() {

    }

    @Override
    public void update(INDArray gradient, String paramType) {

    }

    @Override
    public double score() {
        return 0;
    }

    @Override
    public void computeGradientAndScore() {

    }

    @Override
    public void accumulateScore(double accum) {

    }

    @Override
    public INDArray params() {
        return Nd4j.create(0);
    }

    @Override
    public int numParams() {
        return 0;
    }

    @Override
    public void setParams(INDArray params) {

    }

    @Override
    public void fit(INDArray data) {

    }

    @Override
    public void iterate(INDArray input) {

    }

    @Override
    public Gradient gradient() {
        return gradient;
    }

    @Override
    public Pair<Gradient, Double> gradientAndScore() {
        return new Pair<>(gradient(),score());
    }

    @Override
    public int batchSize() {
        return 0;
    }

    @Override
    public NeuralNetConfiguration conf() {
        return conf;
    }

    @Override
    public void setConf(NeuralNetConfiguration conf) {
        this.conf = conf;
    }

    @Override
    public INDArray input() {
        return null;
    }

    @Override
    public void validateInput() {

    }

    @Override
    public ConvexOptimizer getOptimizer() {
        return null;
    }

    @Override
    public INDArray getParam(String param) {
        return params.get(param);
    }

    @Override
    public void initParams() {

    }

    @Override
    public Map<String, INDArray> paramTable() {
        return params;
    }

    @Override
    public void setParamTable(Map<String, INDArray> paramTable) {
        this.params = paramTable;
    }

    @Override
    public void setParam(String key, INDArray val) {
        params.put(key,val);
    }

    @Override
    public void clear() {

    }

    @Override
    public INDArray preOutput(INDArray x) {
        return preOutput(x,TrainingMode.TRAIN);
    }

    @Override
    public INDArray preOutput(INDArray x, TrainingMode training) {
        int[] activationShape = getShape(x);
        org.deeplearning4j.nn.conf.layers.BatchNormalization layerConf = (org.deeplearning4j.nn.conf.layers.BatchNormalization) conf().getLayer();
        //cache the shape
        this.shape = activationShape;
        INDArray mean,var;
        if(training != TrainingMode.TEST && !layerConf.isUseBatchMean()) {
            mean = x.mean(0, 2);
            var = x.var(0, 2);
            var.addi(layerConf.getEps());
        }
        else {
            mean = getParam(BatchNormalizationParamInitializer.AVG_MEAN);
            var = getParam(BatchNormalizationParamInitializer.AVG_VAR);
        }

        std = Transforms.sqrt(var);
        INDArray xMu = Nd4j.getExecutioner().execAndReturn(new BroadcastSubOp(x, mean, x,-1));
        xHat = Nd4j.getExecutioner().execAndReturn(new BroadcastDivOp(xMu,std,xMu.dup(),-1));
        INDArray gamma = getParam(BatchNormalizationParamInitializer.GAMMA);
        INDArray beta = getParam(BatchNormalizationParamInitializer.BETA);
        INDArray out = Nd4j.getExecutioner().execAndReturn(new BroadcastAddOp(xHat,gamma,xHat.dup(),-1));
        out = Nd4j.getExecutioner().execAndReturn(new BroadcastAddOp(out,beta,out,-1));
        double decay = 0.0;
        if(training != TrainingMode.TEST && !layerConf.isUseBatchMean()) {
            if(layerConf.isFinetune()) {
                layerConf.setN(layerConf.getN() + 1);
                decay =  1. / layerConf.getN();
            }
            else
                decay = layerConf.getDecay();
            int m  = activationShape[0] * activationShape[2];
            double  adjust = m / Math.max(m - 1., 1.);
            getParam(BatchNormalizationParamInitializer.AVG_MEAN).muli(decay);
            getParam(BatchNormalizationParamInitializer.AVG_MEAN).addi(mean.mul((1 - decay)));
            getParam(BatchNormalizationParamInitializer.AVG_VAR).muli(decay);
            getParam(BatchNormalizationParamInitializer.AVG_VAR).addi(var.mul((1 - decay) * adjust));

        }

        return out.reshape(x.shape());
    }

    @Override
    public int numParams(boolean backwards) {
        return 0;
    }

    @Override
    public INDArray activate(TrainingMode training) {
        throw new UnsupportedOperationException();
    }

    @Override
    public INDArray activate(INDArray input, TrainingMode training) {
        return preOutput(input,training);
    }

    @Override
    public INDArray preOutput(INDArray x, boolean training) {
        return preOutput(x,training ? TrainingMode.TRAIN : TrainingMode.TEST);
    }

    @Override
    public INDArray activate(boolean training) {
        throw new UnsupportedOperationException();
    }

    @Override
    public INDArray activate(INDArray input, boolean training) {
        return preOutput(input,training);
    }

    @Override
    public INDArray activate() {
        throw new UnsupportedOperationException();

    }

    @Override
    public INDArray activate(INDArray input) {
        throw new UnsupportedOperationException();

    }

    @Override
    public Layer transpose() {
        throw new UnsupportedOperationException();

    }

    @Override
    public Layer clone() {
        throw new UnsupportedOperationException();

    }

    @Override
    public Collection<IterationListener> getListeners() {
        return listeners;
    }

    @Override
    public void setListeners(IterationListener... listeners) {
        this.listeners = new ArrayList<>(Arrays.asList(listeners));
    }

    @Override
    public void setListeners(Collection<IterationListener> listeners) {
        this.listeners = new ArrayList<>(listeners);
    }

    @Override
    public void setIndex(int index) {
        this.index = index;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public void setInput(INDArray input) {

    }

    @Override
    public void setInputMiniBatchSize(int size) {

    }

    @Override
    public int getInputMiniBatchSize() {
        return 0;
    }

    public int[] getShape(INDArray x) {
        if(x.rank() == 3) {
            int leadDim = x.size(0);
            int cDim = getParam(BatchNormalizationParamInitializer.GAMMA).length();
            int rdim = (int) Math.round(((double) x.length() / ((double) leadDim * (double) cDim)));
            if(rdim < 1)
                rdim = 1;
            if(leadDim * cDim * rdim != x.length())
                throw new IllegalArgumentException("Illegal input for batch size");
            return new int[] {leadDim,cDim,rdim};
        }
        else if(x.rank() == 4) {
            int leadDim = x.size(1);
            int cDim = getParam(BatchNormalizationParamInitializer.GAMMA).length();
            int rdim = (int) Math.round(((double) x.length() / ((double) leadDim * (double) cDim)));
            if(rdim < 1)
                rdim = 1;
            if(leadDim * cDim * rdim != x.length())
                throw new IllegalArgumentException("Illegal input for batch size");
            return new int[] {leadDim,cDim,rdim};
        }

        else throw new IllegalStateException("Unable to process input of rank " + x.rank());
    }

}
