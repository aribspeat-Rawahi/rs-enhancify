"""Convert Real-ESRGAN realesr-general-x4v3 (SRVGGNetCompact) .pth -> TFLite (no torch needed).

Usage: python3 convert_model.py realesr-general-x4v3.pth out.tflite [tile]
"""
import pickle
import sys
import zipfile

import numpy as np
import tensorflow as tf

SCALE = 4


def load_torch_state_dict(path):
    """Minimal torch zip-checkpoint reader that returns numpy arrays."""
    zf = zipfile.ZipFile(path)
    names = zf.namelist()
    prefix = names[0].split("/")[0]
    dtypes = {"FloatStorage": np.float32, "HalfStorage": np.float16, "LongStorage": np.int64}

    class StorageType:
        def __init__(self, name):
            self.dtype = dtypes[name]

    def rebuild_tensor(storage, offset, size, stride, *args):
        if len(size) == 0:
            return storage[offset].copy()
        item = storage.itemsize
        return np.lib.stride_tricks.as_strided(
            storage[offset:], shape=tuple(size), strides=tuple(s * item for s in stride)
        ).copy()

    class Unpickler(pickle.Unpickler):
        def find_class(self, module, name):
            if module == "torch._utils" and name == "_rebuild_tensor_v2":
                return rebuild_tensor
            if module == "torch" and name.endswith("Storage"):
                return StorageType(name)
            if module == "collections" and name == "OrderedDict":
                import collections
                return collections.OrderedDict
            raise pickle.UnpicklingError(f"blocked class {module}.{name}")

        def persistent_load(self, pid):
            _, stype, key, _loc, _numel = pid
            raw = zf.read(f"{prefix}/data/{key}")
            return np.frombuffer(raw, dtype=stype.dtype)

    obj = Unpickler(zf.open(f"{prefix}/data.pkl")).load()
    if "params" in obj:
        obj = obj["params"]
    return obj


def build_keras(sd, tile):
    num_conv = 32
    feat = 64
    inp = tf.keras.Input(shape=(tile, tile, 3), batch_size=1, name="input")
    x = inp
    idx = 0

    def conv(x, i, out_ch, w=None, b=None):
        w = sd[f"body.{i}.weight"] if w is None else w  # (out, in, kh, kw)
        b = sd[f"body.{i}.bias"] if b is None else b
        layer = tf.keras.layers.Conv2D(out_ch, 3, padding="same")
        y = layer(x)
        layer.set_weights([np.transpose(w, (2, 3, 1, 0)), b])
        return y

    def prelu(x, i):
        layer = tf.keras.layers.PReLU(shared_axes=[1, 2])
        y = layer(x)
        layer.set_weights([sd[f"body.{i}.weight"].reshape(1, 1, -1)])
        return y

    x = conv(x, 0, feat)
    x = prelu(x, 1)
    idx = 2
    for _ in range(num_conv):
        x = conv(x, idx, feat)
        x = prelu(x, idx + 1)
        idx += 2

    # Last conv: reorder output channels from torch PixelShuffle order (c, i, j)
    # to TF depth_to_space order (i, j, c).
    w = sd[f"body.{idx}.weight"]
    b = sd[f"body.{idx}.bias"]
    c_out = 3
    r = SCALE
    perm = [c * r * r + i * r + j for i in range(r) for j in range(r) for c in range(c_out)]
    x = conv(x, idx, c_out * r * r, w=w[perm], b=b[perm])
    x = tf.keras.layers.Lambda(lambda t: tf.nn.depth_to_space(t, r))(x)
    base = tf.keras.layers.UpSampling2D(size=r, interpolation="nearest")(inp)
    out = tf.keras.layers.Add(name="output")([x, base])
    return tf.keras.Model(inp, out)


def main():
    src, dst = sys.argv[1], sys.argv[2]
    tile = int(sys.argv[3]) if len(sys.argv) > 3 else 128
    sd = load_torch_state_dict(src)
    model = build_keras(sd, tile)
    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    conv.target_spec.supported_types = [tf.float16]
    data = conv.convert()
    with open(dst, "wb") as f:
        f.write(data)
    print(f"wrote {dst}: {len(data) / 1e6:.2f} MB, tile={tile}")


if __name__ == "__main__":
    main()
