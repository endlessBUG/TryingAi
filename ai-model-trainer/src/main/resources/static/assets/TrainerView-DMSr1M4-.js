import{_ as N}from"./_plugin-vue_export-helper-Bv-FbYbU.js";/* empty css                   *//* empty css                        *//* empty css                *//* empty css                  *//* empty css                   *//* empty css                  */import{g as W,Y as H,u as F,c as K,d as $}from"./YamlEditor-D9PHKFvJ.js";import{d as j,z as Q,o as X,F as E,b as t,w as n,G as s,H as Z,I as J,a as g,L as ee,R as te,c as A,N as ae,X as le,O as i,P as f,Q as oe,aa as ne,e as d,a3 as ie,a4 as re,a7 as se,a5 as ue,Z as de,_ as pe,u as _e,a8 as w,M as me,a6 as fe}from"./index-xne4jhEH.js";const ce={class:"trainer-view"},ge={class:"card-header"},ve={class:"yaml-hint"},ye=`---
job: extension
config:
  name: "{{DATASET_NAME}}"
  process:
    - type: 'sd_trainer'
      training_folder: "output"
      device: cuda:0
      trigger_word: "{{TRIGGER_WORD}}"
      network:
        type: "lora"
        linear: 64
        linear_alpha: 64
      save:
        dtype: float16
        save_every: 250
        max_step_saves_to_keep: 4
      datasets:
        - folder_path: "{{DATASET_PATH}}"
          caption_ext: "txt"
          caption_dropout_rate: 0.05
          num_frames: 1
          resolution: [768]
          worker_nums: 1
          cache_latents_to_disk: true
      train:
        batch_size: 1
        steps: 5000
        gradient_accumulation: 4
        train_unet: true
        train_text_encoder: false
        gradient_checkpointing: true
        noise_scheduler: "flowmatch"
        timestep_type: 'linear'
        optimizer: "adamw8bit"
        lr: 3e-5
        optimizer_params:
          weight_decay: 1e-4
        dtype: bf16
        switch_boundary_every: 10
        cache_text_embeddings: true
      model:
        name_or_path: "ai-toolkit/Wan2.2-T2V-A14B-Diffusers-bf16"
        arch: 'wan22_14b'
        quantize: true
        qtype: "uint4|ostris/accuracy_recovery_adapters/wan22_14b_t2i_torchao_uint4.safetensors"
        quantize_te: true
        qtype_te: "qfloat8"
        low_vram: true
        cache_latents_to_disk: true
        model_kwargs:
          train_high_noise: true
          train_low_noise: true
      sample:
        sampler: "flowmatch"
        sample_every: 250
        width: 768
        height: 768
        num_frames: 1
        fps: 16
        prompts:
          - "{{TRIGGER_WORD}}, a beautiful woman with long black hair"
          - "{{TRIGGER_WORD}}, a woman holding a coffee cup, in a beanie, sitting at a cafe"
        neg: ""
        seed: 42
        walk_seed: true
        guidance_scale: 3.5
        sample_steps: 25
meta:
  name: "[name]"
  version: '1.0'
`,he=`---
sdxl_arguments:
  sdxl: true
model_arguments:
  pretrained_model_name_or_path: "stabilityai/stable-diffusion-xl-base-1.0"
training_arguments:
  output_dir: "output"
  output_name: "my_lora"
  save_every_n_epochs: 1
  max_train_epochs: 10
  train_batch_size: 1
  resolution: "1024,1024"
  enable_bucket: true
  min_bucket_reso: 256
  max_bucket_reso: 2048
  learning_rate: 1e-4
  lr_scheduler: "cosine_with_restarts"
  optimizer_type: "AdamW8bit"
  mixed_precision: "bf16"
  gradient_checkpointing: true
  seed: 42
  cache_latents: true
  cache_text_encoder_outputs: true
dataset_arguments:
  train_data_dir: "{{DATASET_PATH}}"
network_arguments:
  network_module: "networks.lora"
  network_dim: 32
  network_alpha: 16
`,be=j({__name:"TrainerView",setup(we){const v=s(!1),V=s([]),p=s(!1),m=s(!1),y=s(""),h=s(!1),k=s(),T={"ai-toolkit":{label:"AI Toolkit",gitUrl:"https://github.com/ostris/ai-toolkit.git",pythonVersion:"3.10",yaml:ye},"kohya-ss":{label:"Kohya SS",gitUrl:"https://github.com/bmaltais/kohya_ss.git",pythonVersion:"3.10",yaml:he}},U=Object.entries(T).map(([l,e])=>({label:e.label,value:l})),o=s({name:"",type:"",path:"",gitUrl:"",pythonVersion:"",defaultYamlConfig:""});Q(()=>o.value.type,l=>{if(!l||m.value)return;const e=T[l];e&&(o.value.gitUrl=e.gitUrl,o.value.pythonVersion=e.pythonVersion,o.value.defaultYamlConfig=e.yaml)});const x={name:[{required:!0,message:"请输入训练器名称",trigger:"blur"}],gitUrl:[{validator:(l,e,r)=>{!o.value.gitUrl&&!o.value.path?r(new Error("Git 地址和存放地址至少填一个")):r()},trigger:"blur"}],pythonVersion:[{required:!0,message:"请输入Python版本",trigger:"blur"}]};async function b(){v.value=!0;try{const l=await W();V.value=l.data||[]}finally{v.value=!1}}function C(){m.value=!1,y.value="",o.value={name:"",type:"",path:"",gitUrl:"",pythonVersion:"",defaultYamlConfig:""},p.value=!0}function R(l){m.value=!0,y.value=l.id||"",o.value={name:l.name,type:l.type||"",path:l.path||"",gitUrl:l.gitUrl||"",pythonVersion:l.pythonVersion,defaultYamlConfig:l.defaultYamlConfig||""},p.value=!0}async function D(){var l;await((l=k.value)==null?void 0:l.validate()),h.value=!0;try{m.value?(await F(y.value,o.value),w.success("更新成功")):(await K(o.value),w.success("新增成功")),p.value=!1,await b()}finally{h.value=!1}}async function G(l){await $(l),w.success("删除成功"),await b()}return X(b),(l,e)=>{const r=oe,u=ae,Y=le,I=ne,S=me,z=Z,c=se,_=re,L=fe,O=ue,P=ie,M=J,q=te;return g(),E("div",ce,[t(z,null,{header:n(()=>[d("div",ge,[e[9]||(e[9]=d("span",null,"训练器管理",-1)),t(r,{type:"primary",onClick:C},{default:n(()=>[...e[8]||(e[8]=[i("新增训练器",-1)])]),_:1})])]),default:n(()=>[ee((g(),A(S,{data:V.value,stripe:""},{default:n(()=>[t(u,{prop:"name",label:"训练器名称","min-width":"140"}),t(u,{prop:"type",label:"类型",width:"130"},{default:n(({row:a})=>[t(Y,{size:"small"},{default:n(()=>[i(f(a.type||"-"),1)]),_:2},1024)]),_:1}),t(u,{prop:"gitUrl",label:"Git 地址","min-width":"220","show-overflow-tooltip":""}),t(u,{label:"存放地址","min-width":"220","show-overflow-tooltip":""},{default:n(({row:a})=>[i(f(a.path||"（训练时自动下载）"),1)]),_:1}),t(u,{prop:"pythonVersion",label:"Python 版本",width:"120"}),t(u,{prop:"createdAt",label:"创建时间",width:"170"}),t(u,{label:"操作",width:"160",fixed:"right"},{default:n(({row:a})=>[t(r,{size:"small",onClick:B=>R(a)},{default:n(()=>[...e[10]||(e[10]=[i("编辑",-1)])]),_:1},8,["onClick"]),t(I,{title:"确定删除该训练器吗？",onConfirm:B=>G(a.id)},{reference:n(()=>[t(r,{size:"small",type:"danger"},{default:n(()=>[...e[11]||(e[11]=[i("删除",-1)])]),_:1})]),_:1},8,["onConfirm"])]),_:1})]),_:1},8,["data"])),[[q,v.value]])]),_:1}),t(M,{modelValue:p.value,"onUpdate:modelValue":e[7]||(e[7]=a=>p.value=a),title:m.value?"编辑训练器":"新增训练器",width:"700px"},{footer:n(()=>[t(r,{onClick:e[6]||(e[6]=a=>p.value=!1)},{default:n(()=>[...e[17]||(e[17]=[i("取消",-1)])]),_:1}),t(r,{type:"primary",loading:h.value,onClick:D},{default:n(()=>[...e[18]||(e[18]=[i("确定",-1)])]),_:1},8,["loading"])]),default:n(()=>[t(P,{ref_key:"formRef",ref:k,model:o.value,rules:x,"label-width":"110px"},{default:n(()=>[t(_,{label:"训练器名称",prop:"name"},{default:n(()=>[t(c,{modelValue:o.value.name,"onUpdate:modelValue":e[0]||(e[0]=a=>o.value.name=a),placeholder:"请输入训练器名称"},null,8,["modelValue"])]),_:1}),t(_,{label:"类型",prop:"type"},{default:n(()=>[t(O,{modelValue:o.value.type,"onUpdate:modelValue":e[1]||(e[1]=a=>o.value.type=a),placeholder:"请选择训练器类型",style:{width:"100%"}},{default:n(()=>[(g(!0),E(de,null,pe(_e(U),a=>(g(),A(L,{key:a.value,label:a.label,value:a.value},null,8,["label","value"]))),128))]),_:1},8,["modelValue"])]),_:1}),t(_,{label:"Git 地址",prop:"gitUrl"},{default:n(()=>[t(c,{modelValue:o.value.gitUrl,"onUpdate:modelValue":e[2]||(e[2]=a=>o.value.gitUrl=a),placeholder:"例如: https://github.com/ostris/ai-toolkit.git"},null,8,["modelValue"])]),_:1}),t(_,{label:"存放地址"},{default:n(()=>[t(c,{modelValue:o.value.path,"onUpdate:modelValue":e[3]||(e[3]=a=>o.value.path=a),placeholder:"可选，留空则训练时自动从 Git 下载"},null,8,["modelValue"]),e[12]||(e[12]=d("div",{class:"field-hint"},"填写绝对路径可跳过 Git 下载，如 C:\\ai-toolkit 或 /opt/ai-toolkit",-1))]),_:1}),t(_,{label:"Python 版本",prop:"pythonVersion"},{default:n(()=>[t(c,{modelValue:o.value.pythonVersion,"onUpdate:modelValue":e[4]||(e[4]=a=>o.value.pythonVersion=a),placeholder:"例如: 3.10"},null,8,["modelValue"])]),_:1}),t(_,{label:"默认YAML配置"},{default:n(()=>[t(H,{modelValue:o.value.defaultYamlConfig,"onUpdate:modelValue":e[5]||(e[5]=a=>o.value.defaultYamlConfig=a),height:"420px"},null,8,["modelValue"]),d("div",ve,[e[13]||(e[13]=i("占位符: ",-1)),d("code",null,f(l.DATASET_PATH),1),e[14]||(e[14]=i(" 数据集路径, ",-1)),d("code",null,f(l.DATASET_NAME),1),e[15]||(e[15]=i(" LoRA名称, ",-1)),d("code",null,f(l.TRIGGER_WORD),1),e[16]||(e[16]=i(" 触发词",-1))])]),_:1})]),_:1},8,["model"])]),_:1},8,["modelValue","title"])])}}}),De=N(be,[["__scopeId","data-v-cb23d3b9"]]);export{De as default};
