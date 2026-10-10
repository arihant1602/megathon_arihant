import torch, torch.nn as nn
m = nn.Sequential(nn.Conv2d(3, 8, 3, padding=1), nn.ReLU(), nn.AdaptiveAvgPool2d(1), nn.Flatten(), nn.Linear(8, 10)).eval()
torch.onnx.export(m, torch.randn(1, 3, 224, 224), "models/smoke/tiny.onnx", input_names=["input"], output_names=["logits"], opset_version=17)
print("exported")
