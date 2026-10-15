// Time-stretch a velocità variabile, senza cambiare il tono.
// Usa Signalsmith Stretch (MIT). Nessuna dipendenza da Android: si testa anche su PC.
#pragma once

#include <vector>
#include <cmath>
#include <algorithm>
#include "signalsmith-stretch/signalsmith-stretch.h"

namespace vivo {

// Curva di velocità a tratti costanti sul tempo del video ORIGINALE.
// segEnd[i] = fine del tratto i in campioni dell'ingresso, speed[i] = velocità (2 = doppia).
struct SpeedCurve {
    std::vector<double> segEnd;
    std::vector<double> speed;

    double speedAt(double x) const {
        if (speed.empty()) return 1.0;
        for (size_t i = 0; i < segEnd.size(); ++i) {
            if (x < segEnd[i]) return speed[i];
        }
        return speed.back();
    }

    // Posizione in uscita (in campioni) del campione d'ingresso x.
    double outPos(double x) const {
        if (speed.empty()) return x;
        if (x <= 0) return x / speed.front();
        double acc = 0, start = 0;
        for (size_t i = 0; i < segEnd.size(); ++i) {
            double end = segEnd[i];
            if (x <= end) return acc + (x - start) / speed[i];
            acc += (end - start) / speed[i];
            start = end;
        }
        return acc + (x - start) / speed.back();
    }
};

// in: campioni interleaved (frames × channels). Restituisce l'uscita interleaved.
inline std::vector<float> stretchVariable(const std::vector<float> &in, int channels, int sampleRate,
                                          const SpeedCurve &curve) {
    using Stretch = signalsmith::stretch::SignalsmithStretch<float>;
    const long frames = long(in.size() / channels);
    const long outTotal = std::lround(curve.outPos(double(frames)));
    std::vector<float> result(size_t(std::max(0L, outTotal)) * channels, 0.0f);
    if (frames == 0 || outTotal <= 0) return result;

    Stretch stretch(12345);
    stretch.presetDefault(channels, float(sampleRate));
    const int inLat = stretch.inputLatency();
    const int outLat = stretch.outputLatency();
    const double r0 = curve.speedAt(0);

    // Il flusso in uscita è in ritardo di "drop" campioni rispetto al risultato finale.
    const double shift = inLat / r0;
    const long drop = std::lround(shift + outLat);
    const long streamTotal = outTotal + drop;

    const int block = 512;
    std::vector<std::vector<float>> inBuf(channels, std::vector<float>(block));
    std::vector<std::vector<float>> outBuf(channels);

    long fed = 0, written = 0;
    while (written < streamTotal) {
        for (int c = 0; c < channels; ++c) {
            for (int i = 0; i < block; ++i) {
                long f = fed + i;
                inBuf[c][i] = (f < frames) ? in[size_t(f) * channels + c] : 0.0f;
            }
        }
        fed += block;
        // Posizione interna (centro del blocco analizzato) = ingresso fornito − latenza.
        double internal = double(fed - inLat);
        long target = std::lround(curve.outPos(internal) + shift);
        target = std::min(std::max(target, written), streamTotal);
        int outN = int(target - written);
        for (auto &b : outBuf) b.assign(size_t(std::max(outN, 1)), 0.0f);
        struct Ch {
            std::vector<std::vector<float>> *v;
            float *operator[](int c) { return (*v)[size_t(c)].data(); }
        };
        Ch inCh{&inBuf}, outCh{&outBuf};
        stretch.process(inCh, block, outCh, outN);
        for (int i = 0; i < outN; ++i) {
            long pos = written + i - drop;
            if (pos >= 0 && pos < outTotal) {
                for (int c = 0; c < channels; ++c) result[size_t(pos) * channels + c] = outBuf[c][i];
            }
        }
        written += outN;
        // Protezione contro cicli infiniti in caso di curve anomale.
        if (fed > frames + long(sampleRate) * 10 + inLat * 4 && outN == 0) break;
    }
    // Brevissima dissolvenza in entrata/uscita per evitare click.
    const long fade = std::min<long>(outTotal / 2, sampleRate / 200);
    for (long i = 0; i < fade; ++i) {
        float g = float(i) / float(fade);
        for (int c = 0; c < channels; ++c) {
            result[size_t(i) * channels + c] *= g;
            result[size_t(outTotal - 1 - i) * channels + c] *= g;
        }
    }
    return result;
}

} // namespace vivo
