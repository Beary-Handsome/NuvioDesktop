use clap::Parser;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Parser, Serialize, Deserialize)]
#[command(name = "Nuvio Player", version = "1.0.0")]
pub struct PlayerConfig {
    #[arg(long)]
    pub url: String,

    #[arg(long, default_value = "")]
    pub title: String,

    #[arg(long, default_value = "libmpv/nuvio")]
    pub user_agent: String,

    #[arg(long, default_value = "")]
    pub referer: String,

    #[arg(long, default_value_t = 0.0)]
    pub start_time: f64,

    #[arg(long, default_value = "auto")]
    pub hwdec: String,

    #[arg(long, default_value_t = 100)]
    pub volume: i64,

    #[arg(long)]
    pub sub_file: Option<String>,
}

pub fn parse_cli() -> PlayerConfig {
    PlayerConfig::parse()
}
