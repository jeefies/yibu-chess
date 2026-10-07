#!/usr/bin/env python3
"""Export the pinned official Maia-3 5M checkpoint and verify portable inference."""
from pathlib import Path
from types import SimpleNamespace
from collections import deque
import hashlib, json, sys, time
import numpy as np
import torch
import torch.nn as nn
import onnxruntime as ort
from onnxruntime.quantization import quantize_dynamic, QuantType

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'vendor/maia3'))
from maia3.models import MAIA3Model
from maia3.model_registry import resolve_model_spec
from maia3.dataset import tokenize_board, get_legal_moves_mask
from maia3.utils import get_all_possible_moves
import chess

torch.set_num_threads(2)
torch.backends.mha.set_fastpath_enabled(False)
checkpoint = ROOT / 'vendor/maia3/model/maia3-5m.pt'
cfg = SimpleNamespace(**resolve_model_spec('5m').config)
model = MAIA3Model(cfg).eval()
state = torch.load(checkpoint, map_location='cpu', weights_only=True)
model.load_state_dict({k.replace('smolgen', 'gab'): v for k, v in state.items()}, strict=True)

class PortableRmsNorm(nn.Module):
    def __init__(self, norm):
        super().__init__()
        self.weight = norm.weight
        self.eps = norm.eps
    def forward(self, x):
        eps = self.eps if self.eps is not None else torch.finfo(x.dtype).eps
        return x * torch.rsqrt(x.pow(2).mean(dim=-1, keepdim=True) + eps) * self.weight

def portable_norms(module):
    for name, child in list(module.named_children()):
        if isinstance(child, nn.RMSNorm): setattr(module, name, PortableRmsNorm(child))
        else: portable_norms(child)
portable_norms(model)

class PolicyOnly(nn.Module):
    def __init__(self, network): super().__init__(); self.network = network
    def forward(self, tokens, self_elo, opponent_elo):
        return self.network(tokens, self_elo, opponent_elo)[0]

work = ROOT / 'artifacts/maia-export'; work.mkdir(parents=True, exist_ok=True)
out = ROOT / 'app/src/main/assets/models/maia3-5m-int8.onnx'
float_path = work / 'maia3-5m-float.onnx'
example = (torch.zeros(1,64,96), torch.tensor([1000]), torch.tensor([1000]))
with torch.no_grad():
    torch.onnx.export(PolicyOnly(model).eval(), example, str(float_path),
        input_names=['tokens','self_elo','opponent_elo'], output_names=['policy'],
        opset_version=17, dynamo=False)
quantize_dynamic(str(float_path), str(out), weight_type=QuantType.QInt8, per_channel=True,
    reduce_range=False, op_types_to_quantize=['MatMul'])
options = ort.SessionOptions(); options.intra_op_num_threads = 2
sessions = [ort.InferenceSession(str(p), options, providers=['CPUExecutionProvider']) for p in [float_path, out]]
vocab = get_all_possible_moves(); move_index = {m:i for i,m in enumerate(vocab)}
positions = [[], ['e2e4'], ['d2d4'], ['e2e4','e7e5','g1f3'],
    ['e2e4','c7c5','g1f3','d7d6','d2d4','c5d4','f3d4'],
    ['e2e4','e7e5','g1f3','b8c6','f1c4','g8f6'],
    ['e2e4','a7a6','e4e5','d7d5'],
    ['a2a4','h7h5','a4a5','h5h4','a5a6','h4h3','a6b7','h3g2'],
    ['a2a4','h7h5','a4a5','h5h4','a5a6','h4h3','a6b7','h3g2','b7a8q']]
fixtures=[]; report=[]
for history in positions:
    board = chess.Board(); tokens = deque([tokenize_board(board)], maxlen=8)
    for uci in history:
        move=chess.Move.from_uci(uci); assert move in board.legal_moves
        board.push(move); tokens.append(tokenize_board(board))
    padded = [tokens[0]] * (8-len(tokens)) + list(tokens)
    inputs = torch.cat(padded, dim=1).unsqueeze(0)
    mask = get_legal_moves_mask(board, move_index).numpy()
    legal_indices=np.flatnonzero(mask)
    for elo in [600,1000,1800,2600]:
        feed={'tokens':inputs.numpy(),'self_elo':np.array([elo],dtype=np.int64),'opponent_elo':np.array([elo],dtype=np.int64)}
        with torch.no_grad(): reference=model(inputs,torch.tensor([elo]),torch.tensor([elo]))[0].numpy()[0]
        full=sessions[0].run(None,feed)[0][0]
        quant=sessions[1].run(None,feed)[0][0]
        assert np.max(np.abs(reference-full)) < 0.0002
        def probabilities(logits):
            values=logits[mask]; ex=np.exp(values-values.max()); return ex/ex.sum()
        a,b=probabilities(reference),probabilities(quant)
        tv=float(np.abs(a-b).sum()/2)
        assert tv < .045, (history,elo,tv)
        report.append({'plies':len(history),'elo':elo,'total_variation':tv,'same_top_move':int(a.argmax())==int(b.argmax())})
        if elo==1000:
            pieces=inputs.numpy()[0].reshape(-1)
            fixtures.append({'history':history,'fen':board.fen(),'tokenOnes':[int(i) for i in np.flatnonzero(pieces)],
                'legalIndices':legal_indices.tolist(), 'logits':{str(i):float(quant[i]) for i in legal_indices}})
for name,session in zip(['float','int8'],sessions):
    start=time.perf_counter()
    for _ in range(50): session.run(None,feed)
    print(name,'average ms',round((time.perf_counter()-start)*20,2))
metadata={'model':'Maia-3 5M','upstream_commit':'1e13597c42d4858b7cfd7cfdae01e297263364b2',
    'checkpoint_revision':'b6559de2398d7140b985f28fd2c19fb5e47ddabe','checkpoint_sha256':hashlib.sha256(checkpoint.read_bytes()).hexdigest(),
    'onnx_sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'onnx_bytes':out.stat().st_size,
    'torch_version':torch.__version__,'onnxruntime_version':ort.__version__,'history':8,'token_dimensions':96,
    'move_vocabulary':4352,'quantization':'per-channel dynamic QInt8 MatMul, full range',
    'verification_positions':len(report),'max_policy_total_variation':max(x['total_variation'] for x in report),
    'top_move_agreement':sum(x['same_top_move'] for x in report)/len(report),'license':'AGPL-3.0'}
(ROOT/'app/src/main/assets/models/maia3-metadata.json').write_text(json.dumps(metadata,indent=2))
(ROOT/'core/src/test/resources/maia/reference-fixtures.json').write_text(json.dumps(fixtures,separators=(',',':')))
(work/'validation.json').write_text(json.dumps(report,indent=2))
print(json.dumps(metadata,indent=2))
