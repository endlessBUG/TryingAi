import{_ as K}from"./_plugin-vue_export-helper-bb27QPe_.js";/* empty css                   *//* empty css                        *//* empty css                *//* empty css                  *//* empty css                   *//* empty css                  */import{g as $,u as F,c as N,d as j}from"./trainer-Q7V71_pj.js";import{Y as J}from"./YamlEditor-DvCsEN5h.js";import{d as R,a8 as W,o as Z,A as U,b as t,w as o,z as s,B as Q,C as X,a as c,G as ee,M as te,c as x,I as ae,S as le,J as i,K as b,L as oe,a4 as ne,e as _,Z as ie,_ as re,a1 as se,$ as ue,T as de,U as pe,u as me,a2 as w,H as _e,a0 as fe}from"./index-CrsnlAaz.js";const ce={class:"trainer-view"},ge={class:"card-header"},ve={class:"yaml-hint"},he=`---
job: extension
config:
  name: "my_first_wan22_14b_lora_v1"
  process:
    - type: 'sd_trainer'
      training_folder: "output"
      device: cuda:0
      network:
        type: "lora"
        linear: 32
        linear_alpha: 32
      save:
        dtype: float16
        save_every: 250
        max_step_saves_to_keep: 4
      datasets:
        - folder_path: "{{DATASET_PATH}}"
          caption_ext: "txt"
          caption_dropout_rate: 0.05
          num_frames: 1
          resolution: [512, 768, 1024]
      train:
        batch_size: 1
        steps: 2000
        gradient_accumulation: 1
        train_unet: true
        train_text_encoder: false
        gradient_checkpointing: true
        noise_scheduler: "flowmatch"
        timestep_type: 'linear'
        optimizer: "adamw8bit"
        lr: 1e-4
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
        model_kwargs:
          train_high_noise: true
          train_low_noise: true
      sample:
        sampler: "flowmatch"
        sample_every: 250
        width: 1024
        height: 1024
        num_frames: 1
        fps: 16
        prompts:
          - "woman with red hair, playing chess at the park, bomb going off in the background"
          - "a woman holding a coffee cup, in a beanie, sitting at a cafe"
          - "a horse is a DJ at a night club, fish eye lens, smoke machine, lazer lights, holding a martini"
        neg: ""
        seed: 42
        walk_seed: true
        guidance_scale: 3.5
        sample_steps: 25
meta:
  name: "[name]"
  version: '1.0'
`,ye=`---
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
`,be=R({__name:"TrainerView",setup(we){const g=s(!1),V=s([]),d=s(!1),m=s(!1),v=s(""),h=s(!1),k=s(),T={"ai-toolkit":{label:"AI Toolkit",gitUrl:"https://github.com/ostris/ai-toolkit.git",pythonVersion:"3.10",yaml:he},"kohya-ss":{label:"Kohya SS",gitUrl:"https://github.com/bmaltais/kohya_ss.git",pythonVersion:"3.10",yaml:ye}},E=Object.entries(T).map(([n,e])=>({label:e.label,value:n})),l=s({name:"",type:"",path:"",gitUrl:"",pythonVersion:"",defaultYamlConfig:""});W(()=>l.value.type,n=>{if(!n||m.value)return;const e=T[n];e&&(l.value.gitUrl=e.gitUrl,l.value.pythonVersion=e.pythonVersion,l.value.defaultYamlConfig=e.yaml)});const A={name:[{required:!0,message:"请输入训练器名称",trigger:"blur"}],gitUrl:[{validator:(n,e,r)=>{!l.value.gitUrl&&!l.value.path?r(new Error("Git 地址和存放地址至少填一个")):r()},trigger:"blur"}],pythonVersion:[{required:!0,message:"请输入Python版本",trigger:"blur"}]};async function y(){g.value=!0;try{const n=await $();V.value=n.data||[]}finally{g.value=!1}}function C(){m.value=!1,v.value="",l.value={name:"",type:"",path:"",gitUrl:"",pythonVersion:"",defaultYamlConfig:""},d.value=!0}function Y(n){m.value=!0,v.value=n.id||"",l.value={name:n.name,type:n.type||"",path:n.path||"",gitUrl:n.gitUrl||"",pythonVersion:n.pythonVersion,defaultYamlConfig:n.defaultYamlConfig||""},d.value=!0}async function z(){var n;await((n=k.value)==null?void 0:n.validate()),h.value=!0;try{m.value?(await F(v.value,l.value),w.success("更新成功")):(await N(l.value),w.success("新增成功")),d.value=!1,await y()}finally{h.value=!1}}async function S(n){await j(n),w.success("删除成功"),await y()}return Z(y),(n,e)=>{const r=oe,u=ae,D=le,L=ne,I=_e,P=Q,f=se,p=re,q=fe,B=ue,G=ie,M=X,H=te;return c(),U("div",ce,[t(P,null,{header:o(()=>[_("div",ge,[e[9]||(e[9]=_("span",null,"训练器管理",-1)),t(r,{type:"primary",onClick:C},{default:o(()=>[...e[8]||(e[8]=[i("新增训练器",-1)])]),_:1})])]),default:o(()=>[ee((c(),x(I,{data:V.value,stripe:""},{default:o(()=>[t(u,{prop:"name",label:"训练器名称","min-width":"140"}),t(u,{prop:"type",label:"类型",width:"130"},{default:o(({row:a})=>[t(D,{size:"small"},{default:o(()=>[i(b(a.type||"-"),1)]),_:2},1024)]),_:1}),t(u,{prop:"gitUrl",label:"Git 地址","min-width":"220","show-overflow-tooltip":""}),t(u,{label:"存放地址","min-width":"220","show-overflow-tooltip":""},{default:o(({row:a})=>[i(b(a.path||"（训练时自动下载）"),1)]),_:1}),t(u,{prop:"pythonVersion",label:"Python 版本",width:"120"}),t(u,{prop:"createdAt",label:"创建时间",width:"170"}),t(u,{label:"操作",width:"160",fixed:"right"},{default:o(({row:a})=>[t(r,{size:"small",onClick:O=>Y(a)},{default:o(()=>[...e[10]||(e[10]=[i("编辑",-1)])]),_:1},8,["onClick"]),t(L,{title:"确定删除该训练器吗？",onConfirm:O=>S(a.id)},{reference:o(()=>[t(r,{size:"small",type:"danger"},{default:o(()=>[...e[11]||(e[11]=[i("删除",-1)])]),_:1})]),_:1},8,["onConfirm"])]),_:1})]),_:1},8,["data"])),[[H,g.value]])]),_:1}),t(M,{modelValue:d.value,"onUpdate:modelValue":e[7]||(e[7]=a=>d.value=a),title:m.value?"编辑训练器":"新增训练器",width:"700px"},{footer:o(()=>[t(r,{onClick:e[6]||(e[6]=a=>d.value=!1)},{default:o(()=>[...e[15]||(e[15]=[i("取消",-1)])]),_:1}),t(r,{type:"primary",loading:h.value,onClick:z},{default:o(()=>[...e[16]||(e[16]=[i("确定",-1)])]),_:1},8,["loading"])]),default:o(()=>[t(G,{ref_key:"formRef",ref:k,model:l.value,rules:A,"label-width":"110px"},{default:o(()=>[t(p,{label:"训练器名称",prop:"name"},{default:o(()=>[t(f,{modelValue:l.value.name,"onUpdate:modelValue":e[0]||(e[0]=a=>l.value.name=a),placeholder:"请输入训练器名称"},null,8,["modelValue"])]),_:1}),t(p,{label:"类型",prop:"type"},{default:o(()=>[t(B,{modelValue:l.value.type,"onUpdate:modelValue":e[1]||(e[1]=a=>l.value.type=a),placeholder:"请选择训练器类型",style:{width:"100%"}},{default:o(()=>[(c(!0),U(de,null,pe(me(E),a=>(c(),x(q,{key:a.value,label:a.label,value:a.value},null,8,["label","value"]))),128))]),_:1},8,["modelValue"])]),_:1}),t(p,{label:"Git 地址",prop:"gitUrl"},{default:o(()=>[t(f,{modelValue:l.value.gitUrl,"onUpdate:modelValue":e[2]||(e[2]=a=>l.value.gitUrl=a),placeholder:"例如: https://github.com/ostris/ai-toolkit.git"},null,8,["modelValue"])]),_:1}),t(p,{label:"存放地址"},{default:o(()=>[t(f,{modelValue:l.value.path,"onUpdate:modelValue":e[3]||(e[3]=a=>l.value.path=a),placeholder:"可选，留空则训练时自动从 Git 下载"},null,8,["modelValue"]),e[12]||(e[12]=_("div",{class:"field-hint"},"填写绝对路径可跳过 Git 下载，如 C:\\ai-toolkit 或 /opt/ai-toolkit",-1))]),_:1}),t(p,{label:"Python 版本",prop:"pythonVersion"},{default:o(()=>[t(f,{modelValue:l.value.pythonVersion,"onUpdate:modelValue":e[4]||(e[4]=a=>l.value.pythonVersion=a),placeholder:"例如: 3.10"},null,8,["modelValue"])]),_:1}),t(p,{label:"默认YAML配置"},{default:o(()=>[t(J,{modelValue:l.value.defaultYamlConfig,"onUpdate:modelValue":e[5]||(e[5]=a=>l.value.defaultYamlConfig=a),height:"420px"},null,8,["modelValue"]),_("div",ve,[e[13]||(e[13]=i("数据集路径请使用 ",-1)),_("code",null,b(n.DATASET_PATH),1),e[14]||(e[14]=i(" 占位符",-1))])]),_:1})]),_:1},8,["model"])]),_:1},8,["modelValue","title"])])}}}),Se=K(be,[["__scopeId","data-v-9c4f7ed0"]]);export{Se as default};
